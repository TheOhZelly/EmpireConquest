package com.empireconquest.listeners;

import com.empireconquest.EmpireConquest;
import com.empireconquest.Phase;
import com.empireconquest.objects.EmpTeam;
import com.empireconquest.objects.MacuahuitlItem;
import com.empireconquest.objects.MusketItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles:
 *  - Musket firing (left-click), cooldown action bar, ray-trace hit detection
 *  - Macuahuitl speed effect management
 */
public class CombatListener implements Listener {

    private static final double MUSKET_DAMAGE    = 25.0;
    private static final double MUSKET_RANGE     = 50.0;
    private static final double BLOOM_THRESHOLD  = 20.0;  // blocks beyond which bloom applies
    private static final double BLOOM_MAX_DEGREES = 8.0;
    private static final long   COOLDOWN_TICKS   = 100L;  // 5 seconds

    private final EmpireConquest plugin;
    /** UUID → last fire time in milliseconds */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public CombatListener(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    // ── Musket ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (plugin.getPhase() != Phase.CONQUEST) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!MusketItem.isMusket(held, plugin)) return;

        // Only on left-click actions
        switch (event.getAction()) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> {}
            default -> { return; }
        }

        event.setCancelled(true);

        // Team check
        EmpTeam team = plugin.getTeamManager().getTeam(player.getUniqueId());
        if (team != EmpTeam.SPANISH) {
            player.sendMessage("§cOnly Spanish players can fire a musket.");
            return;
        }

        // Cooldown check
        long now = System.currentTimeMillis();
        long lastFired = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cooldownMs = COOLDOWN_TICKS * 50L; // ticks → ms
        if (now - lastFired < cooldownMs) {
            long remaining = cooldownMs - (now - lastFired);
            player.sendMessage(Component.text("Still reloading! (" + String.format("%.1f", remaining / 1000.0) + "s)", NamedTextColor.RED));
            return;
        }

        // Check for musket balls in inventory
        ItemStack ball = findMusketBall(player);
        if (ball == null) {
            player.sendMessage(Component.text("No Musket Balls in your inventory!", NamedTextColor.RED));
            return;
        }

        // Consume one ball
        if (ball.getAmount() > 1) {
            ball.setAmount(ball.getAmount() - 1);
        } else {
            player.getInventory().remove(ball);
        }

        cooldowns.put(player.getUniqueId(), now);
        fireMusket(player);
        startReloadBar(player);
    }

    private ItemStack findMusketBall(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (MusketItem.isMusketBall(item, plugin)) return item;
        }
        return null;
    }

    private void fireMusket(Player player) {
        Vector direction = player.getEyeLocation().getDirection().normalize();

        // Straight ray trace first to find any target
        RayTraceResult straight = player.getWorld().rayTraceEntities(
            player.getEyeLocation(),
            direction,
            MUSKET_RANGE,
            entity -> entity instanceof Player && !entity.equals(player) && entity instanceof LivingEntity
        );

        Entity target = null;

        if (straight != null && straight.getHitEntity() != null) {
            double dist = straight.getHitPosition().distance(player.getEyeLocation().toVector());
            if (dist <= BLOOM_THRESHOLD) {
                // Close range: guaranteed hit
                target = straight.getHitEntity();
            } else {
                // Far range: apply bloom — re-trace with bloomed direction
                Vector bloomed = applyBloom(direction, BLOOM_MAX_DEGREES);
                RayTraceResult bloomResult = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    bloomed,
                    MUSKET_RANGE,
                    entity -> entity instanceof Player && !entity.equals(player) && entity instanceof LivingEntity
                );
                if (bloomResult != null && bloomResult.getHitEntity() != null) {
                    target = bloomResult.getHitEntity();
                }
            }
        }

        if (target instanceof LivingEntity livingTarget) {
            livingTarget.damage(MUSKET_DAMAGE, player);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.0f);
            player.sendMessage(Component.text("Hit! (" + String.format("%.1f", MUSKET_DAMAGE / 2.0) + " hearts)", NamedTextColor.GOLD));
        } else {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 0.8f);
        }
    }

    /** Rotates a vector by a random angle up to maxDegrees in a random azimuthal direction. */
    private Vector applyBloom(Vector direction, double maxDegrees) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double angle   = Math.toRadians(rng.nextDouble(0, maxDegrees));
        double azimuth = rng.nextDouble(0, Math.PI * 2);

        // Build a perpendicular basis
        Vector ref  = Math.abs(direction.getX()) < 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector perp = direction.clone().crossProduct(ref).normalize();
        Vector perp2 = direction.clone().crossProduct(perp).normalize();

        // Random perpendicular direction at this azimuth
        Vector randomPerp = perp.clone().multiply(Math.cos(azimuth))
            .add(perp2.multiply(Math.sin(azimuth)));

        return direction.clone().multiply(Math.cos(angle))
            .add(randomPerp.multiply(Math.sin(angle)))
            .normalize();
    }

    private void startReloadBar(Player player) {
        new BukkitRunnable() {
            int tick = 0;
            final int maxTicks = (int) COOLDOWN_TICKS;

            @Override
            public void run() {
                if (!player.isOnline() || tick >= maxTicks) {
                    player.sendActionBar(Component.empty());
                    cancel();
                    return;
                }
                int filled = (tick * 10) / maxTicks;
                String bar = "§c[§6" + "|".repeat(filled) + "§7" + "|".repeat(10 - filled) + "§c]";
                player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize("§cReloading: " + bar));
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Macuahuitl speed effect ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // Check what they're switching away from
        ItemStack prev = player.getInventory().getItem(event.getPreviousSlot());
        if (MacuahuitlItem.isMacuahuitl(prev, plugin)) {
            // Switching away from macuahuitl — remove speed unless new item is also macuahuitl
            ItemStack next = player.getInventory().getItem(event.getNewSlot());
            if (!MacuahuitlItem.isMacuahuitl(next, plugin)) {
                player.removePotionEffect(PotionEffectType.SPEED);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (MacuahuitlItem.isMacuahuitl(event.getItemDrop().getItemStack(), plugin)) {
            // Dropping the macuahuitl — remove speed (will be re-applied by task if still holding one)
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!MacuahuitlItem.isMacuahuitl(held, plugin)) {
                player.removePotionEffect(PotionEffectType.SPEED);
            }
        }
    }

    /** Called every 20 ticks from EmpireConquest's repeating task during CONQUEST. */
    public void tickMacuahuitlSpeed() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            EmpTeam team = plugin.getTeamManager().getTeam(player.getUniqueId());
            if (team != EmpTeam.AZTEC) continue;

            ItemStack held = player.getInventory().getItemInMainHand();
            if (MacuahuitlItem.isMacuahuitl(held, plugin)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, false));
            }
        }
    }

    /** Clear all cooldowns (called on /emp end). */
    public void reset() {
        cooldowns.clear();
    }
}
