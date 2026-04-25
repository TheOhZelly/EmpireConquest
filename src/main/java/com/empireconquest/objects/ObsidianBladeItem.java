package com.empireconquest.objects;

import com.empireconquest.EmpireConquest;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public final class ObsidianBladeItem {

    private ObsidianBladeItem() {}

    public static ItemStack createObsidianBlade(EmpireConquest plugin) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§8Obsidian Blade");
        meta.setLore(List.of("§7Razor-edged Aztec weapon", "§aGrants Speed while held"));
        meta.setCustomModelData(1004);

        // 11 total attack damage: base 1 + 10 modifier = diamond sword + 2 hearts
        meta.addAttributeModifier(
            Attribute.GENERIC_ATTACK_DAMAGE,
            new AttributeModifier(
                UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012"),
                "empireconquest.obsidian_blade.damage",
                10.0,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlot.HAND
            )
        );

        meta.getPersistentDataContainer()
            .set(plugin.getItemTypeKey(), PersistentDataType.STRING, "obsidian_blade");
        item.setItemMeta(meta);
        return item;
    }

    // Recipe: OO_ / OO_ / _S_   (O=Obsidian, S=Stick) — 4 obsidian + 1 stick
    public static void registerRecipe(EmpireConquest plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "obsidian_blade");
        ItemStack result = createObsidianBlade(plugin);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("OO ", "OO ", " S ");
        recipe.setIngredient('O', Material.OBSIDIAN);
        recipe.setIngredient('S', Material.STICK);
        plugin.getServer().addRecipe(recipe);
    }

    public static boolean isObsidianBlade(ItemStack item, EmpireConquest plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        String type = item.getItemMeta().getPersistentDataContainer()
            .get(plugin.getItemTypeKey(), PersistentDataType.STRING);
        return "obsidian_blade".equals(type);
    }
}
