package com.empireconquest.listeners;

import com.empireconquest.EmpireConquest;
import com.empireconquest.objects.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

public class CraftListener implements Listener {

    private final EmpireConquest plugin;

    public CraftListener(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !result.hasItemMeta()) return;

        boolean isSpanishItem = MusketItem.isMusket(result, plugin)
                             || MusketItem.isIronBall(result, plugin)
                             || FlintlockItem.isFlintlock(result, plugin);

        boolean isAztecItem   = MacuahuitlItem.isMacuahuitl(result, plugin)
                             || ObsidianBladeItem.isObsidianBlade(result, plugin);

        if (!isSpanishItem && !isAztecItem) return;

        for (HumanEntity viewer : event.getViewers()) {
            EmpTeam team = plugin.getTeamManager().getTeam(viewer.getUniqueId());

            if (isSpanishItem && team != EmpTeam.SPANISH) {
                event.getInventory().setResult(null);
                return;
            }

            if (isAztecItem && team != EmpTeam.AZTEC) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
