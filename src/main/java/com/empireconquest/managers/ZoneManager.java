package com.empireconquest.managers;

import com.empireconquest.EmpireConquest;
import com.empireconquest.objects.CaptureZone;
import com.empireconquest.objects.EmpTeam;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ZoneManager {

    private final EmpireConquest plugin;
    private final List<CaptureZone> zones = new ArrayList<>();

    /** Per-player wand state: index 0 = pos1, index 1 = pos2, null entry = not set. */
    private final Map<UUID, Location[]>  wandSelections = new HashMap<>();
    /** Whether the player's next wand right-click sets pos1 (true) or pos2 (false). */
    private final Map<UUID, Boolean>     wandState      = new HashMap<>();
    /** Per-player boss bar showing closest zone. */
    private final Map<UUID, BossBar>     playerBossBars = new HashMap<>();

    private BukkitTask tugTask;
    private BukkitTask particleTask;
    private BukkitTask bossBarTask;

    public ZoneManager(EmpireConquest plugin) {
        this.plugin = plugin;
        loadZones();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void loadZones() {
        zones.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("zones");
        if (section == null) return;

        for (String name : section.getKeys(false)) {
            ConfigurationSection zs = section.getConfigurationSection(name);
            if (zs == null) continue;
            World world = Bukkit.getWorld(zs.getString("world", "world"));
            if (world == null) {
                plugin.getLogger().warning("Unknown world for zone '" + name + "', skipping.");
                continue;
            }
            Location c1 = new Location(world, zs.getDouble("x1"), zs.getDouble("y1"), zs.getDouble("z1"));
            Location c2 = new Location(world, zs.getDouble("x2"), zs.getDouble("y2"), zs.getDouble("z2"));
            zones.add(new CaptureZone(name, c1, c2));
        }
    }

    public void saveZone(CaptureZone zone) {
        String path = "zones." + zone.getName() + ".";
        plugin.getConfig().set(path + "world", zone.getCorner1().getWorld().getName());
        plugin.getConfig().set(path + "x1", zone.getCorner1().getBlockX());
        plugin.getConfig().set(path + "y1", zone.getCorner1().getBlockY());
        plugin.getConfig().set(path + "z1", zone.getCorner1().getBlockZ());
        plugin.getConfig().set(path + "x2", zone.getCorner2().getBlockX());
        plugin.getConfig().set(path + "y2", zone.getCorner2().getBlockY());
        plugin.getConfig().set(path + "z2", zone.getCorner2().getBlockZ());
        plugin.saveConfig();
    }

    /** Registers a new zone. Returns false if name already exists. */
    public boolean addZone(String name, Location pos1, Location pos2) {
        for (CaptureZone z : zones) {
            if (z.getName().equalsIgnoreCase(name)) return false;
        }
        CaptureZone zone = new CaptureZone(name, pos1, pos2);
        zones.add(zone);
        saveZone(zone);
        return true;
    }

    // ── Wand API ──────────────────────────────────────────────────────────────

    public void wandRightClick(Player player, Location clicked) {
        UUID uuid  = player.getUniqueId();
        boolean settingPos1 = wandState.getOrDefault(uuid, true);

        Location[] sel = wandSelections.computeIfAbsent(uuid, k -> new Location[2]);
        if (settingPos1) {
            sel[0] = clicked.clone();
            wandState.put(uuid, false);
            player.sendMessage("§a[Wand] §fPos1 set to §e" + formatLoc(clicked));
        } else {
            sel[1] = clicked.clone();
            wandState.put(uuid, true);
            player.sendMessage("§a[Wand] §fPos2 set to §e" + formatLoc(clicked) + "§f. Run §a/emp addzone <name>§f to confirm.");
        }
    }

    public Location[] getWandSelection(UUID uuid) {
        return wandSelections.get(uuid);
    }

    private String formatLoc(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
    }

    // ── Conquest lifecycle ────────────────────────────────────────────────────

    public void startConquest() {
        for (CaptureZone zone : zones) {
            zone.setActive(true);
            zone.setProgress(0.5f);
            zone.setOwner(null);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            showBossBar(p);
        }
        startTasks();
    }

    public void stopConquest() {
        cancelTasks();
        for (BossBar bar : playerBossBars.values()) bar.removeAll();
        playerBossBars.clear();
        for (CaptureZone zone : zones) zone.setActive(false);
    }

    // Called from player join/quit during conquest
    public void showBossBar(Player player) {
        if (playerBossBars.containsKey(player.getUniqueId())) return;
        BossBar bar = Bukkit.createBossBar("§eNo active zones", BarColor.WHITE, BarStyle.SOLID);
        bar.addPlayer(player);
        playerBossBars.put(player.getUniqueId(), bar);
    }

    public void hideBossBar(Player player) {
        BossBar bar = playerBossBars.remove(player.getUniqueId());
        if (bar != null) bar.removePlayer(player);
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    private void startTasks() {
        tugTask = new BukkitRunnable() {
            @Override public void run() { tickTugOfWar(); }
        }.runTaskTimer(plugin, 10L, 10L);

        particleTask = new BukkitRunnable() {
            @Override public void run() { tickParticles(); }
        }.runTaskTimer(plugin, 5L, 5L);

        bossBarTask = new BukkitRunnable() {
            @Override public void run() { tickBossBars(); }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void cancelTasks() {
        if (tugTask      != null) { tugTask.cancel();      tugTask      = null; }
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
        if (bossBarTask  != null) { bossBarTask.cancel();  bossBarTask  = null; }
    }

    // ── Tug of War ────────────────────────────────────────────────────────────

    private void tickTugOfWar() {
        if (plugin.isPaused()) return;

        for (CaptureZone zone : zones) {
            if (!zone.isActive()) continue;

            int spanishIn = 0, aztecIn = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!zone.contains(p.getLocation())) continue;
                EmpTeam t = plugin.getTeamManager().getTeam(p.getUniqueId());
                if (t == EmpTeam.SPANISH) spanishIn++;
                else if (t == EmpTeam.AZTEC) aztecIn++;
            }

            float delta = 0f;

            if (spanishIn > 0 && aztecIn == 0) {
                double mult = Math.min(5.0, 1.0 + (spanishIn - 1) * (4.0 / 9.0));
                delta = (float) (0.005 * mult);
            } else if (aztecIn > 0 && spanishIn == 0) {
                double mult = Math.min(5.0, 1.0 + (aztecIn - 1) * (4.0 / 9.0));
                delta = -(float) (0.005 * mult);
            } else if (spanishIn > 0 && aztecIn > 0) {
                if (spanishIn > aztecIn)       delta =  0.0025f;
                else if (aztecIn > spanishIn)  delta = -0.0025f;
                // equal count → frozen (delta stays 0)
            }

            if (delta == 0f) continue;

            EmpTeam prevOwner = zone.getOwner();
            zone.setProgress(zone.getProgress() + delta);

            EmpTeam newOwner = prevOwner;
            if (zone.getProgress() >= 1.0f) newOwner = EmpTeam.SPANISH;
            else if (zone.getProgress() <= 0.0f) newOwner = EmpTeam.AZTEC;

            if (newOwner != prevOwner) {
                zone.setOwner(newOwner);
                onOwnershipChange(zone);
            }
        }
    }

    private void onOwnershipChange(CaptureZone zone) {
        Location center = zone.getCenter();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getWorld().playSound(center, Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }

        String ownerName = zone.getOwner() == null ? "Neutral"
            : (zone.getOwner() == EmpTeam.SPANISH ? "§cSpanish" : "§9Aztec");
        Bukkit.broadcastMessage("§6[EmpireConquest] §e" + zone.getName() + " §fis now held by " + ownerName + "§f!");

        plugin.getLifeManager().checkLivesWin();
    }

    // ── Particles ─────────────────────────────────────────────────────────────

    private static final int PARTICLE_ROWS = 5;

    private void tickParticles() {
        for (CaptureZone zone : zones) {
            if (!zone.isActive()) continue;

            Color color;
            if      (zone.getOwner() == EmpTeam.SPANISH) color = Color.fromRGB(255, 255, 255); // white
            else if (zone.getOwner() == EmpTeam.AZTEC)   color = Color.fromRGB(255, 140,   0); // orange
            else                                          color = Color.fromRGB(180, 180, 180); // neutral gray

            Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
            Location center = zone.getCenter();

            List<Player> near = center.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(center) <= 10000)
                .toList();

            if (near.isEmpty()) continue;
            drawHorizontalRings(zone, near, dust);
        }
    }

    /** Draws PARTICLE_ROWS horizontal rectangular perimeters evenly spread across the zone's height. */
    private void drawHorizontalRings(CaptureZone zone, List<Player> players, Particle.DustOptions dust) {
        Location c1 = zone.getCorner1(), c2 = zone.getCorner2();
        int x1 = Math.min(c1.getBlockX(), c2.getBlockX()), x2 = Math.max(c1.getBlockX(), c2.getBlockX());
        int y1 = Math.min(c1.getBlockY(), c2.getBlockY()), y2 = Math.max(c1.getBlockY(), c2.getBlockY());
        int z1 = Math.min(c1.getBlockZ(), c2.getBlockZ()), z2 = Math.max(c1.getBlockZ(), c2.getBlockZ());
        World world = c1.getWorld();

        for (int row = 0; row < PARTICLE_ROWS; row++) {
            double t = PARTICLE_ROWS == 1 ? 0.5 : (double) row / (PARTICLE_ROWS - 1);
            int y = (int) Math.round(y1 + (y2 - y1) * t);

            drawLine(world, players, dust, x1, y, z1, x2, y, z1);
            drawLine(world, players, dust, x2, y, z1, x2, y, z2);
            drawLine(world, players, dust, x2, y, z2, x1, y, z2);
            drawLine(world, players, dust, x1, y, z2, x1, y, z1);
        }
    }

    private void drawLine(World world, List<Player> players, Particle.DustOptions dust,
                          int ax, int ay, int az, int bx, int by, int bz) {
        int dx = bx - ax, dy = by - ay, dz = bz - az;
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps == 0) steps = 1;

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = ax + dx * t + 0.5;
            double y = ay + dy * t + 0.5;
            double z = az + dz * t + 0.5;
            for (Player p : players) {
                p.spawnParticle(Particle.REDSTONE, x, y, z, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    // ── Boss Bars ─────────────────────────────────────────────────────────────

    private void tickBossBars() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            BossBar bar = playerBossBars.get(p.getUniqueId());
            if (bar == null) continue;

            CaptureZone closest = null;
            double minDist = Double.MAX_VALUE;
            for (CaptureZone zone : zones) {
                if (!zone.isActive()) continue;
                double d = zone.getCenter().distanceSquared(p.getLocation());
                if (d < minDist) { minDist = d; closest = zone; }
            }

            if (closest == null) {
                bar.setTitle("§eNo active zones");
                bar.setColor(BarColor.WHITE);
                bar.setProgress(0.5);
                continue;
            }

            float prog = closest.getProgress();
            int spanishBars = Math.round(prog * 10);
            int aztecBars   = 10 - spanishBars;
            String fill = "§c" + "|".repeat(spanishBars) + "§9" + "|".repeat(aztecBars);
            bar.setTitle("§e[" + closest.getName() + "] §7| §cS: " + fill + " §9A");
            bar.setProgress(Math.max(0.0, Math.min(1.0, prog)));

            if      (closest.getOwner() == EmpTeam.SPANISH) bar.setColor(BarColor.RED);
            else if (closest.getOwner() == EmpTeam.AZTEC)   bar.setColor(BarColor.BLUE);
            else                                             bar.setColor(BarColor.WHITE);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public List<CaptureZone> getZones() { return zones; }

    public CaptureZone getZone(String name) {
        for (CaptureZone z : zones) {
            if (z.getName().equalsIgnoreCase(name)) return z;
        }
        return null;
    }
}
