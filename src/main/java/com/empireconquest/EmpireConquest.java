package com.empireconquest;

import com.empireconquest.commands.EmpCommand;
import com.empireconquest.listeners.*;
import com.empireconquest.managers.*;
import com.empireconquest.objects.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * EmpireConquest — Two-phase Spanish Conquest vs Aztecs event plugin.
 *
 *  Phase.PREPARATION (default): PvP off, zone building blocked, no life tracking.
 *  Phase.CONQUEST:              PvP on, life pools active, zone capture active.
 */
public class EmpireConquest extends JavaPlugin {

    // ── Shared PDC key for all custom items ───────────────────────────────────
    private NamespacedKey itemTypeKey;

    // ── Plugin-wide state ─────────────────────────────────────────────────────
    private Phase   phase     = Phase.PREPARATION;
    private boolean paused    = false;
    private boolean pvpEnabled = false;
    private int     timeRemaining = 0; // seconds, countdown

    // ── Managers ──────────────────────────────────────────────────────────────
    private TeamManager      teamManager;
    private ZoneManager      zoneManager;
    private LifeManager      lifeManager;
    private ScoreboardManager scoreboardManager;

    // ── Listeners (kept so we can call methods on them) ───────────────────────
    private CombatListener combatListener;

    // ── Repeating tasks ───────────────────────────────────────────────────────
    private BukkitTask gameTimerTask;
    private BukkitTask speedTask;
    private BukkitTask winCheckTask;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        // Save default resource files
        saveDefaultConfig();
        saveResource("teams.yml", false);

        // PDC key used by all custom items
        itemTypeKey = new NamespacedKey(this, "type");

        // Initialise managers
        teamManager       = new TeamManager(this);
        zoneManager       = new ZoneManager(this);
        lifeManager       = new LifeManager(this);
        scoreboardManager = new ScoreboardManager(this);

        // Register recipes
        MusketItem.registerRecipes(this);
        FlintlockItem.registerRecipe(this);
        MacuahuitlItem.registerRecipe(this);
        ObsidianBladeItem.registerRecipe(this);

        // Register listeners
        combatListener = new CombatListener(this);
        getServer().getPluginManager().registerEvents(new PhaseListener(this),    this);
        getServer().getPluginManager().registerEvents(new ZoneListener(this),     this);
        getServer().getPluginManager().registerEvents(new CraftListener(this),    this);
        getServer().getPluginManager().registerEvents(combatListener,             this);
        getServer().getPluginManager().registerEvents(new DeathListener(this),    this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);

        // Register command
        getCommand("emp").setExecutor(new EmpCommand(this));
        getCommand("emp").setTabCompleter(new EmpCommand(this));

        getLogger().info("EmpireConquest enabled — Phase: PREPARATION");
    }

    @Override
    public void onDisable() {
        if (phase == Phase.CONQUEST) {
            endEvent();
        }
        getLogger().info("EmpireConquest disabled.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event control
    // ─────────────────────────────────────────────────────────────────────────

    /** Transitions from PREPARATION → CONQUEST and starts all systems. */
    public void startConquest() {
        phase  = Phase.CONQUEST;
        paused = false;

        lifeManager.initialize();
        zoneManager.startConquest();
        scoreboardManager.startConquest();

        int timerMinutes = getConfig().getInt("timer-minutes", 45);
        timeRemaining = timerMinutes * 60;

        // Game timer (counts down every second)
        gameTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (paused) return;
                if (timeRemaining > 0) {
                    timeRemaining--;
                } else {
                    cancel();
                    lifeManager.checkTimerWin();
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        // Macuahuitl speed refresh every 20 ticks
        speedTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (phase == Phase.CONQUEST) combatListener.tickWeaponSpeed();
            }
        }.runTaskTimer(this, 20L, 20L);

        // Safety win-condition check every 30 seconds
        winCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (phase == Phase.CONQUEST) lifeManager.checkLivesWin();
            }
        }.runTaskTimer(this, 600L, 600L);
    }

    /**
     * Cleanly ends the event: cancels all tasks, resets scoreboards/boss bars,
     * returns phase to PREPARATION.
     */
    public void endEvent() {
        // Cancel game tasks
        cancelTask(gameTimerTask);  gameTimerTask = null;
        cancelTask(speedTask);      speedTask     = null;
        cancelTask(winCheckTask);   winCheckTask  = null;

        // Stop sub-system tasks
        zoneManager.stopConquest();
        scoreboardManager.stopConquest();

        // Reset state
        combatListener.reset();
        lifeManager.reset();

        phase         = Phase.PREPARATION;
        paused        = false;
        timeRemaining = 0;

        getLogger().info("EmpireConquest event ended — Phase: PREPARATION");
    }

    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) task.cancel();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Safe-point persistence
    // ─────────────────────────────────────────────────────────────────────────

    public Location getSafePoint(EmpTeam team) {
        String key = "safe-points." + team.name().toLowerCase();
        if (!getConfig().contains(key + ".world")) return null;
        World world = Bukkit.getWorld(getConfig().getString(key + ".world", "world"));
        if (world == null) return null;
        return new Location(world,
            getConfig().getDouble(key + ".x"),
            getConfig().getDouble(key + ".y"),
            getConfig().getDouble(key + ".z"));
    }

    public void setSafePoint(EmpTeam team, Location loc) {
        String key = "safe-points." + team.name().toLowerCase();
        getConfig().set(key + ".world", loc.getWorld().getName());
        getConfig().set(key + ".x", loc.getX());
        getConfig().set(key + ".y", loc.getY());
        getConfig().set(key + ".z", loc.getZ());
        saveConfig();
    }

    /** Best-effort map centre: average of all safe-point locations. */
    public Location getMapCenter() {
        Location s = getSafePoint(EmpTeam.SPANISH);
        Location a = getSafePoint(EmpTeam.AZTEC);
        if (s == null && a == null) return null;
        if (s == null) return a;
        if (a == null) return s;
        if (!s.getWorld().equals(a.getWorld())) return s;
        return new Location(s.getWorld(),
            (s.getX() + a.getX()) / 2,
            (s.getY() + a.getY()) / 2,
            (s.getZ() + a.getZ()) / 2);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────────────────

    public NamespacedKey       getItemTypeKey()      { return itemTypeKey; }
    public Phase               getPhase()             { return phase; }
    public boolean             isPaused()             { return paused; }
    public int                 getTimeRemaining()     { return timeRemaining; }
    public TeamManager         getTeamManager()       { return teamManager; }
    public ZoneManager         getZoneManager()       { return zoneManager; }
    public LifeManager         getLifeManager()       { return lifeManager; }
    public ScoreboardManager   getScoreboardManager() { return scoreboardManager; }

    /** Toggles the paused state; returns the new state. */
    public boolean togglePause() {
        paused = !paused;
        return paused;
    }

    /** Toggles free PvP (usable outside of CONQUEST); returns the new state. */
    public boolean togglePvp() {
        pvpEnabled = !pvpEnabled;
        return pvpEnabled;
    }

    public boolean isPvpEnabled() { return pvpEnabled; }
}
