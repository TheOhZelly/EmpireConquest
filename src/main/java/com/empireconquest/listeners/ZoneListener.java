package com.empireconquest.listeners;

import com.empireconquest.EmpireConquest;
import com.empireconquest.objects.CaptureZone;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Block break/place inside CaptureZone cuboids is always prevented (both phases).
 * Also handles the zone-wand (BLAZE_ROD) right-click selection for OPs.
 */
public class ZoneListener implements Listener {

    private final EmpireConquest plugin;

    public ZoneListener(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    // ── Block protection ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (insideAnyZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot break blocks inside a capture zone.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (insideAnyZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot place blocks inside a capture zone.");
        }
    }

    private boolean insideAnyZone(org.bukkit.Location loc) {
        for (CaptureZone zone : plugin.getZoneManager().getZones()) {
            if (zone.contains(loc)) return true;
        }
        return false;
    }

    // ── Zone wand ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.BLAZE_ROD) return;
        if (!player.hasPermission("empireconquest.admin")) return;

        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK -> {
                event.setCancelled(true);
                plugin.getZoneManager().wandRightClick(player, event.getClickedBlock().getLocation());
            }
            case RIGHT_CLICK_AIR -> {
                event.setCancelled(true);
                plugin.getZoneManager().wandRightClick(player, player.getLocation());
            }
            default -> {}
        }
    }
}
