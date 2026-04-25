package com.empireconquest.managers;

import com.empireconquest.EmpireConquest;
import com.empireconquest.objects.CaptureZone;
import com.empireconquest.objects.EmpTeam;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.List;

public class ScoreboardManager {

    private static final int MAX_LINES = 16;

    private final EmpireConquest plugin;
    private Scoreboard board;
    private Objective  objective;
    private final org.bukkit.scoreboard.Team[] lineTeams = new org.bukkit.scoreboard.Team[MAX_LINES];
    private final String[] entries = new String[MAX_LINES];
    private BukkitTask updateTask;

    public ScoreboardManager(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void buildBoard() {
        board     = Bukkit.getScoreboardManager().getNewScoreboard();
        objective = board.registerNewObjective("empire_sb", "dummy", "§6§l─ CONQUEST ─");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int i = 0; i < MAX_LINES; i++) {
            // Unique invisible entry using two ChatColor codes
            entries[i] = ChatColor.values()[i % 16].toString() + ChatColor.RESET;
            org.bukkit.scoreboard.Team t = board.registerNewTeam("ec_line" + i);
            t.addEntry(entries[i]);
            lineTeams[i] = t;
        }
    }

    private void setLine(int idx, String text) {
        if (idx < 0 || idx >= MAX_LINES) return;
        lineTeams[idx].setPrefix(text.length() > 64 ? text.substring(0, 64) : text);
        objective.getScore(entries[idx]).setScore(MAX_LINES - idx);
    }

    private void clearLine(int idx) {
        if (idx < 0 || idx >= MAX_LINES) return;
        board.resetScores(entries[idx]);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void startConquest() {
        buildBoard();
        for (Player p : Bukkit.getOnlinePlayers()) p.setScoreboard(board);

        updateTask = new BukkitRunnable() {
            @Override public void run() { update(); }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stopConquest() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }

        Scoreboard def = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) p.setScoreboard(def);

        board     = null;
        objective = null;
    }

    public void showToPlayer(Player player) {
        if (board != null) player.setScoreboard(board);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    private void update() {
        if (board == null) return;

        int remaining = plugin.getTimeRemaining();
        int mm = remaining / 60;
        int ss = remaining % 60;
        String time = String.format("%02d:%02d", mm, ss);

        int spanishLives = plugin.getLifeManager().getLives(EmpTeam.SPANISH);
        int aztecLives   = plugin.getLifeManager().getLives(EmpTeam.AZTEC);
        int maxSpanish   = plugin.getLifeManager().getMaxLives(EmpTeam.SPANISH);
        int maxAztec     = plugin.getLifeManager().getMaxLives(EmpTeam.AZTEC);

        // Line 0: time
        setLine(0, "§fTime: §e" + time);
        // Line 1: spanish lives
        setLine(1, "§cSpanish: §f" + lifeBar(spanishLives, maxSpanish) + " §c" + spanishLives);
        // Line 2: aztec lives
        setLine(2, "§9Aztec:   §f" + lifeBar(aztecLives, maxAztec) + " §9" + aztecLives);
        // Line 3: blank
        setLine(3, "");
        // Line 4: domination header
        setLine(4, "§eDOMINATION:");

        List<CaptureZone> zones = plugin.getZoneManager().getZones();
        int maxZoneLines = MAX_LINES - 5; // lines 5 through MAX_LINES-1
        int zoneCount    = Math.min(zones.size(), maxZoneLines);

        for (int i = 0; i < zoneCount; i++) {
            CaptureZone z = zones.get(i);
            String ownerTag = z.getOwner() == null ? "§7~"
                : (z.getOwner() == EmpTeam.SPANISH ? "§cS" : "§9A");
            setLine(5 + i, "  §f" + z.getName() + ": [" + ownerTag + "§f]");
        }

        // Clear any stale zone lines if zone list shrank
        for (int i = zoneCount; i < maxZoneLines; i++) {
            clearLine(5 + i);
        }
    }

    private String lifeBar(int current, int max) {
        if (max <= 0) return "[§7----------§f]";
        int filled = Math.max(0, Math.min(10, (int) Math.round(10.0 * current / max)));
        return "[§a" + "|".repeat(filled) + "§7" + "|".repeat(10 - filled) + "§f]";
    }

    public void cancelTasks() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
    }
}
