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
            entries[i] = ChatColor.values()[i % 16].toString() + ChatColor.RESET;
            org.bukkit.scoreboard.Team t = board.registerNewTeam("ec_line" + i);
            t.addEntry(entries[i]);
            lineTeams[i] = t;
        }

        // Set up nametag color teams on the conquest scoreboard
        plugin.getTeamManager().ensureNametagTeams(board);
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
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.getTeamManager().applyNametag(p, board);
            p.setScoreboard(board);
        }

        updateTask = new BukkitRunnable() {
            @Override public void run() { update(); }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stopConquest() {
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }

        org.bukkit.scoreboard.Scoreboard def = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) p.setScoreboard(def);

        board     = null;
        objective = null;
    }

    public void showToPlayer(Player player) {
        if (board != null) {
            plugin.getTeamManager().applyNametag(player, board);
            player.setScoreboard(board);
        }
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

        setLine(0, "§fTime: §e" + time);
        setLine(1, "§fSpanish: " + lifeBar(spanishLives, maxSpanish) + " §f" + spanishLives);
        setLine(2, "§6Aztec:   " + lifeBar(aztecLives, maxAztec) + " §6" + aztecLives);
        setLine(3, "");
        setLine(4, "§eDOMINATION:");

        List<CaptureZone> zones = plugin.getZoneManager().getZones();
        int maxZoneLines = MAX_LINES - 5;
        int zoneCount    = Math.min(zones.size(), maxZoneLines);

        for (int i = 0; i < zoneCount; i++) {
            CaptureZone z = zones.get(i);
            String ownerTag = z.getOwner() == null ? "§7~"
                : (z.getOwner() == EmpTeam.SPANISH ? "§fS" : "§6A");
            setLine(5 + i, "  §7" + z.getName() + ": [" + ownerTag + "§7]");
        }

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
