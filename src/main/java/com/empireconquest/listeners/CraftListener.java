package com.empireconquest.listeners;

import com.empireconquest.EmpireConquest;
import com.empireconquest.objects.EmpTeam;
import com.empireconquest.objects.MacuahuitlItem;
import com.empireconquest.objects.MusketItem;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents players from crafting team-locked items if they are on the wrong team.
 * Uses PersistentDataContainer tags — never lore or name matching.
 */
public class CraftListener implements Listener {

    private final EmpireConquest plugin;

    public CraftListener(EmpireConquest plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !result.hasItemMeta()) return;

        // Identify which team item this recipe produces
        boolean isMusketRecipe  = MusketItem.isMusket(result, plugin)
                               || MusketItem.isMusketBall(result, plugin);
        boolean isMacuahuitl    = MacuahuitlItem.isMacuahuitl(result, plugin);

        if (!isMusketRecipe && !isMacuahuitl) return;

        // Check every viewer — if any viewer is on the wrong team, block it
        for (HumanEntity viewer : event.getViewers()) {
            EmpTeam team = plugin.getTeamManager().getTeam(viewer.getUniqueId());

            if (isMusketRecipe && team != EmpTeam.SPANISH) {
                event.getInventory().setResult(null);
                return;
            }

            if (isMacuahuitl && team != EmpTeam.AZTEC) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
