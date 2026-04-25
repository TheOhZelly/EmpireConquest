package com.empireconquest.objects;

import com.empireconquest.EmpireConquest;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class FlintlockItem {

    private FlintlockItem() {}

    public static ItemStack createFlintlock(EmpireConquest plugin) {
        ItemStack item = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§eFlintlock Pistol");
        meta.setLore(List.of("§7A compact Spanish sidearm", "§eLeft-click to fire"));
        meta.setCustomModelData(1002);
        meta.getPersistentDataContainer()
            .set(plugin.getItemTypeKey(), PersistentDataType.STRING, "flintlock");
        item.setItemMeta(meta);
        return item;
    }

    // Recipe: II_ / GS_ / ___   (I=Iron Ingot, G=Gunpowder, S=Stick)
    public static void registerRecipe(EmpireConquest plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "flintlock");
        ItemStack result = createFlintlock(plugin);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("II ", "GS ", "   ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('G', Material.GUNPOWDER);
        recipe.setIngredient('S', Material.STICK);
        plugin.getServer().addRecipe(recipe);
    }

    public static boolean isFlintlock(ItemStack item, EmpireConquest plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        String type = item.getItemMeta().getPersistentDataContainer()
            .get(plugin.getItemTypeKey(), PersistentDataType.STRING);
        return "flintlock".equals(type);
    }
}
