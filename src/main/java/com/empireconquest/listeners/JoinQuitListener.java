package com.empireconquest.listeners;

import com.empireconquest.EmpireConquest;
import com.empireconquest.Phase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Manages per-player state on join/quit:
 *  - Shows scoreboard and boss bar to late joiners during CONQUEST.
 *  - Cleans up boss bar on quit.
 */
public class JoinQuitListener implements Listener {

    private final EmpireConquest plugin;

    public JoinQuitListener(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Reload team assignment so joining player has their team loaded
        plugin.getTeamManager().load();

        if (plugin.getPhase() == Phase.CONQUEST) {
            plugin.getScoreboardManager().showToPlayer(event.getPlayer());
            plugin.getZoneManager().showBossBar(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getPhase() == Phase.CONQUEST) {
            plugin.getZoneManager().hideBossBar(event.getPlayer());
            // Trigger a win-condition check in case this was the last living player of a team
            plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getLifeManager().checkLivesWin(), 1L);
        }
    }
}
