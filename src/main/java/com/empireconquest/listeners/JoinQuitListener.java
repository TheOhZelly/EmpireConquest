package com.empireconquest.listeners;

import com.empireconquest.EmpireConquest;
import com.empireconquest.Phase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {

    private final EmpireConquest plugin;

    public JoinQuitListener(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getTeamManager().load();
        // Apply nametag color on the main scoreboard immediately
        plugin.getTeamManager().applyNametag(event.getPlayer());

        if (plugin.getPhase() == Phase.CONQUEST) {
            plugin.getScoreboardManager().showToPlayer(event.getPlayer());
            plugin.getZoneManager().showBossBar(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (plugin.getPhase() == Phase.CONQUEST) {
            plugin.getZoneManager().hideBossBar(event.getPlayer());
            plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getLifeManager().checkLivesWin(), 1L);
        }
    }
}
