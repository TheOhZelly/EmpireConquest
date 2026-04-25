package com.empireconquest.listeners;

import com.empireconquest.EmpireConquest;
import com.empireconquest.Phase;
import com.empireconquest.objects.EmpTeam;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * During CONQUEST phase:
 *  - Decrements team life pool on player death.
 *  - Wipes drops and inventory.
 *  - Sends to spectator if team lives are exhausted.
 *  - Routes respawn to team safe point or map centre.
 */
public class DeathListener implements Listener {

    private final EmpireConquest plugin;

    public DeathListener(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (plugin.getPhase() != Phase.CONQUEST) return;

        Player player = event.getEntity();
        EmpTeam team  = plugin.getTeamManager().getTeam(player.getUniqueId());
        if (team == null) return;

        // Wipe drops and inventory
        event.getDrops().clear();
        player.getInventory().clear();

        // Decrement lives (will also handle "team fallen" title internally)
        plugin.getLifeManager().onPlayerDeath(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (plugin.getPhase() != Phase.CONQUEST) return;

        Player player = event.getPlayer();
        EmpTeam team  = plugin.getTeamManager().getTeam(player.getUniqueId());
        if (team == null) return;

        if (plugin.getLifeManager().isEliminated(team)) {
            // No lives left — keep in spectator, send to map centre
            player.setGameMode(GameMode.SPECTATOR);
            Location centre = plugin.getMapCenter();
            if (centre != null) event.setRespawnLocation(centre);
        } else {
            // Still have lives — respawn at team safe point
            Location safe = plugin.getSafePoint(team);
            if (safe != null) event.setRespawnLocation(safe);
        }

        // Win condition is re-checked after respawn so spectator state is applied first
        plugin.getServer().getScheduler().runTask(plugin,
            () -> plugin.getLifeManager().checkLivesWin());
    }
}
