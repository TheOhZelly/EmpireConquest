package com.empireconquest.managers;

import com.empireconquest.EmpireConquest;
import com.empireconquest.objects.CaptureZone;
import com.empireconquest.objects.EmpTeam;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class LifeManager {

    private final EmpireConquest plugin;
    private final Map<EmpTeam, Integer> lives    = new EnumMap<>(EmpTeam.class);
    private final Map<EmpTeam, Integer> maxLives = new EnumMap<>(EmpTeam.class);
    private boolean winDeclared = false;

    public LifeManager(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Called on /emp start conquest. Computes life pools based on config
     * per-player values and actual online team sizes.
     */
    public void initialize() {
        winDeclared = false;

        int perSpanish = plugin.getConfig().getInt("spanish-lives", 5);
        int perAztec   = plugin.getConfig().getInt("aztec-lives",  10);

        int spanishCount = (int) Bukkit.getOnlinePlayers().stream()
            .filter(p -> plugin.getTeamManager().getTeam(p.getUniqueId()) == EmpTeam.SPANISH)
            .count();
        int aztecCount = (int) Bukkit.getOnlinePlayers().stream()
            .filter(p -> plugin.getTeamManager().getTeam(p.getUniqueId()) == EmpTeam.AZTEC)
            .count();

        // Guarantee at least 1 life per team even if nobody is online
        int spanishTotal = Math.max(1, spanishCount * perSpanish);
        int aztecTotal   = Math.max(1, aztecCount   * perAztec);

        lives.put(EmpTeam.SPANISH, spanishTotal);
        lives.put(EmpTeam.AZTEC,   aztecTotal);
        maxLives.put(EmpTeam.SPANISH, spanishTotal);
        maxLives.put(EmpTeam.AZTEC,   aztecTotal);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int getLives(EmpTeam team) {
        return lives.getOrDefault(team, 0);
    }

    public int getMaxLives(EmpTeam team) {
        return maxLives.getOrDefault(team, 1);
    }

    public boolean isEliminated(EmpTeam team) {
        return getLives(team) <= 0;
    }

    // ── Life pool manipulation ────────────────────────────────────────────────

    /**
     * Decrements the life pool for the dead player's team.
     * If the pool hits 0, sets all team players to spectator and triggers win check.
     */
    public void onPlayerDeath(Player deadPlayer) {
        EmpTeam team = plugin.getTeamManager().getTeam(deadPlayer.getUniqueId());
        if (team == null) return;

        int current = lives.getOrDefault(team, 0);
        if (current > 0) {
            lives.put(team, current - 1);
        }

        if (lives.getOrDefault(team, 0) <= 0) {
            // Send fallen title to the dead player (will be shown on respawn/now)
            deadPlayer.showTitle(Title.title(
                Component.text("YOUR TEAM HAS FALLEN", NamedTextColor.RED),
                Component.text("You are eliminated", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(500))
            ));
        }

        checkLivesWin();
    }

    // ── Win condition checks ──────────────────────────────────────────────────

    /**
     * Lives-based win: triggered after each death and on the 30-second safety check.
     * Wins when a team has 0 lives AND all its members are spectators (no more
     * respawns possible and all active players are out).
     */
    public void checkLivesWin() {
        if (winDeclared) return;

        for (EmpTeam team : EmpTeam.values()) {
            if (lives.getOrDefault(team, 1) > 0) continue;

            // Check if all online players on this team are spectators
            boolean allOut = true;
            for (UUID uuid : plugin.getTeamManager().getTeamPlayers(team)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline() && p.getGameMode() != GameMode.SPECTATOR) {
                    allOut = false;
                    break;
                }
            }

            if (allOut) {
                EmpTeam winner = (team == EmpTeam.SPANISH) ? EmpTeam.AZTEC : EmpTeam.SPANISH;
                declareWinner(winner, "lives depleted");
                return;
            }
        }
    }

    /**
     * Timer-based win: called when the countdown reaches zero.
     * Team with most zones wins; tie → draw.
     */
    public void checkTimerWin() {
        if (winDeclared) return;

        int spanish = 0, aztec = 0;
        for (CaptureZone zone : plugin.getZoneManager().getZones()) {
            if (zone.getOwner() == EmpTeam.SPANISH) spanish++;
            else if (zone.getOwner() == EmpTeam.AZTEC) aztec++;
        }

        if (spanish > aztec) {
            declareWinner(EmpTeam.SPANISH, "zone domination");
        } else if (aztec > spanish) {
            declareWinner(EmpTeam.AZTEC, "zone domination");
        } else {
            declareDraw();
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void declareWinner(EmpTeam winner, String reason) {
        winDeclared = true;
        String color  = winner == EmpTeam.SPANISH ? "§c" : "§9";
        String name   = winner == EmpTeam.SPANISH ? "Spanish" : "Aztec";

        Title title = Title.title(
            Component.text(name + " Victory!", winner == EmpTeam.SPANISH ? NamedTextColor.RED : NamedTextColor.BLUE),
            Component.text("Won by " + reason, NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(6), Duration.ofSeconds(2))
        );

        Bukkit.broadcastMessage(color + "═══════════════════════");
        Bukkit.broadcastMessage(color + "  " + name.toUpperCase() + " WINS!");
        Bukkit.broadcastMessage("§e  Reason: §f" + reason);
        Bukkit.broadcastMessage(color + "═══════════════════════");

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // Schedule cleanup 10 seconds later
        Bukkit.getScheduler().runTaskLater(plugin, plugin::endEvent, 200L);
    }

    private void declareDraw() {
        winDeclared = true;
        Title title = Title.title(
            Component.text("DRAW!", NamedTextColor.YELLOW),
            Component.text("Equal zone control at time's end", NamedTextColor.GRAY),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(6), Duration.ofSeconds(2))
        );
        Bukkit.broadcastMessage("§e══════════ DRAW ══════════");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 0.8f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, plugin::endEvent, 200L);
    }

    public void reset() {
        lives.clear();
        maxLives.clear();
        winDeclared = false;
    }
}
