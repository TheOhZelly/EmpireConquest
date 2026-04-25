package com.empireconquest.listeners;

import com.empireconquest.EmpireConquest;
import com.empireconquest.Phase;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Enforces phase-dependent PvP rules.
 * During PREPARATION: all player-vs-player damage is cancelled.
 * During CONQUEST: PvP is enabled (events fall through).
 */
public class PhaseListener implements Listener {

    private final EmpireConquest plugin;

    public PhaseListener(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Only suppress PvP during PREPARATION
        if (plugin.getPhase() != Phase.PREPARATION) return;

        // Cancel if both attacker and victim are players
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity()  instanceof Player)) return;

        event.setCancelled(true);
    }
}
