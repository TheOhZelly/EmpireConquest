package com.empireconquest.listeners;

import com.empireconquest.EmpireConquest;
import com.empireconquest.Phase;
import com.empireconquest.objects.*;
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

public class CombatListener implements Listener {

    // Musket stats
    private static final double MUSKET_DAMAGE          = 25.0;
    private static final double MUSKET_RANGE           = 50.0;
    private static final double MUSKET_BLOOM_THRESHOLD = 20.0;
    private static final double MUSKET_BLOOM_MAX       = 8.0;
    private static final long   MUSKET_COOLDOWN_TICKS  = 100L; // 5 seconds

    // Flintlock stats — shorter range, faster cooldown, more bloom
    private static final double FLINTLOCK_DAMAGE          = 14.0; // 7 hearts
    private static final double FLINTLOCK_RANGE           = 25.0;
    private static final double FLINTLOCK_BLOOM_THRESHOLD = 10.0;
    private static final double FLINTLOCK_BLOOM_MAX       = 12.0;
    private static final long   FLINTLOCK_COOLDOWN_TICKS  = 40L;  // 2 seconds

    private final EmpireConquest plugin;
    private final Map<UUID, Long> musketCooldowns    = new HashMap<>();
    private final Map<UUID, Long> flintlockCooldowns = new HashMap<>();

    public CombatListener(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    // ── Musket / Flintlock firing ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // getHand() returns null for LEFT_CLICK_AIR — allow null (main hand) but reject off-hand
        EquipmentSlot hand = event.getHand();
        if (hand != null && hand != EquipmentSlot.HAND) return;
        if (plugin.getPhase() != Phase.CONQUEST) return;

        Player    player = event.getPlayer();
        ItemStack held   = player.getInventory().getItemInMainHand();

        boolean isMusket    = MusketItem.isMusket(held, plugin);
        boolean isFlintlock = FlintlockItem.isFlintlock(held, plugin);
        if (!isMusket && !isFlintlock) return;

        switch (event.getAction()) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> {}
            default -> { return; }
        }

        event.setCancelled(true);

        EmpTeam team = plugin.getTeamManager().getTeam(player.getUniqueId());
        if (team != EmpTeam.SPANISH) {
            player.sendMessage("§cOnly Spanish players can use this weapon.");
            return;
        }

        if (isMusket) {
            handleGunFire(player, musketCooldowns, MUSKET_COOLDOWN_TICKS,
                MUSKET_DAMAGE, MUSKET_RANGE, MUSKET_BLOOM_THRESHOLD, MUSKET_BLOOM_MAX);
        } else {
            handleGunFire(player, flintlockCooldowns, FLINTLOCK_COOLDOWN_TICKS,
                FLINTLOCK_DAMAGE, FLINTLOCK_RANGE, FLINTLOCK_BLOOM_THRESHOLD, FLINTLOCK_BLOOM_MAX);
        }
    }

    private void handleGunFire(Player player, Map<UUID, Long> cooldowns, long cooldownTicks,
                                double damage, double range, double bloomThreshold, double bloomMax) {
        long now        = System.currentTimeMillis();
        long lastFired  = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long cooldownMs = cooldownTicks * 50L;

        if (now - lastFired < cooldownMs) {
            long remaining = cooldownMs - (now - lastFired);
            player.sendMessage(Component.text(
                "Still reloading! (" + String.format("%.1f", remaining / 1000.0) + "s)",
                NamedTextColor.RED));
            return;
        }

        ItemStack ball = findIronBall(player);
        if (ball == null) {
            player.sendMessage(Component.text("No Iron Balls in your inventory!", NamedTextColor.RED));
            return;
        }

        if (ball.getAmount() > 1) ball.setAmount(ball.getAmount() - 1);
        else player.getInventory().remove(ball);

        cooldowns.put(player.getUniqueId(), now);
        fireGun(player, damage, range, bloomThreshold, bloomMax);
        startReloadBar(player, cooldownTicks);
    }

    private ItemStack findIronBall(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (MusketItem.isIronBall(item, plugin)) return item;
        }
        return null;
    }

    private void fireGun(Player player, double damage, double range,
                         double bloomThreshold, double bloomMax) {
        Vector direction = player.getEyeLocation().getDirection().normalize();

        RayTraceResult straight = player.getWorld().rayTraceEntities(
            player.getEyeLocation(), direction, range,
            entity -> entity instanceof Player && !entity.equals(player)
        );

        Entity target = null;

        if (straight != null && straight.getHitEntity() != null) {
            double dist = straight.getHitPosition().distance(player.getEyeLocation().toVector());
            if (dist <= bloomThreshold) {
                target = straight.getHitEntity();
            } else {
                Vector bloomed = applyBloom(direction, bloomMax);
                RayTraceResult bloomResult = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(), bloomed, range,
                    entity -> entity instanceof Player && !entity.equals(player)
                );
                if (bloomResult != null) target = bloomResult.getHitEntity();
            }
        }

        if (target instanceof LivingEntity livingTarget) {
            livingTarget.damage(damage, player);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f);
            player.sendMessage(Component.text(
                "Hit! (" + String.format("%.1f", damage / 2.0) + " hearts)",
                NamedTextColor.GOLD));
        } else {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 0.8f);
        }
    }

    private Vector applyBloom(Vector direction, double maxDegrees) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double angle   = Math.toRadians(rng.nextDouble(0, maxDegrees));
        double azimuth = rng.nextDouble(0, Math.PI * 2);

        Vector ref   = Math.abs(direction.getX()) < 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector perp  = direction.clone().crossProduct(ref).normalize();
        Vector perp2 = direction.clone().crossProduct(perp).normalize();

        Vector randomPerp = perp.clone().multiply(Math.cos(azimuth))
            .add(perp2.multiply(Math.sin(azimuth)));

        return direction.clone().multiply(Math.cos(angle))
            .add(randomPerp.multiply(Math.sin(angle)))
            .normalize();
    }

    private void startReloadBar(Player player, long cooldownTicks) {
        new BukkitRunnable() {
            int tick = 0;
            final int maxTicks = (int) cooldownTicks;

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

    // ── Speed weapon helpers ──────────────────────────────────────────────────

    private boolean isSpeedWeapon(ItemStack item) {
        return MacuahuitlItem.isMacuahuitl(item, plugin)
            || ObsidianBladeItem.isObsidianBlade(item, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player    player = event.getPlayer();
        ItemStack prev   = player.getInventory().getItem(event.getPreviousSlot());
        if (isSpeedWeapon(prev)) {
            ItemStack next = player.getInventory().getItem(event.getNewSlot());
            if (!isSpeedWeapon(next)) {
                player.removePotionEffect(PotionEffectType.SPEED);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isSpeedWeapon(event.getItemDrop().getItemStack())) {
            if (!isSpeedWeapon(player.getInventory().getItemInMainHand())) {
                player.removePotionEffect(PotionEffectType.SPEED);
            }
        }
    }

    /** Called every 20 ticks from EmpireConquest's repeating task during CONQUEST. */
    public void tickWeaponSpeed() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            EmpTeam team = plugin.getTeamManager().getTeam(player.getUniqueId());
            if (team != EmpTeam.AZTEC) continue;

            if (isSpeedWeapon(player.getInventory().getItemInMainHand())) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, false));
            }
        }
    }

    /** Clear all cooldowns (called on /emp end). */
    public void reset() {
        musketCooldowns.clear();
        flintlockCooldowns.clear();
    }
}
