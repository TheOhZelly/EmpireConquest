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

public final class MacuahuitlItem {

    private MacuahuitlItem() {}

    // ── Item creation ─────────────────────────────────────────────────────────

    /**
     * Creates a Macuahuitl with +9 attack damage and an attack speed that
     * yields 2.5 swings/sec (base 4.0 − 1.5 = 2.5).
     */
    public static ItemStack createMacuahuitl(EmpireConquest plugin) {
        ItemStack item = new ItemStack(Material.WOODEN_SWORD);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§5Macuahuitl");
        meta.setLore(List.of("§7Obsidian-edged Aztec warclub", "§aGrants Speed while held"));

        // +9 attack damage while in main hand
        meta.addAttributeModifier(
            Attribute.GENERIC_ATTACK_DAMAGE,
            new AttributeModifier(
                UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
                "empireconquest.macuahuitl.damage",
                9.0,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlot.HAND
            )
        );

        // Attack speed: base 4.0 + (-1.5) = 2.5 swings/sec
        meta.addAttributeModifier(
            Attribute.GENERIC_ATTACK_SPEED,
            new AttributeModifier(
                UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"),
                "empireconquest.macuahuitl.speed",
                -1.5,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlot.HAND
            )
        );

        meta.getPersistentDataContainer()
            .set(plugin.getItemTypeKey(), PersistentDataType.STRING, "macuahuitl");
        item.setItemMeta(meta);
        return item;
    }

    // ── Recipe registration ───────────────────────────────────────────────────

    /**
     * Shape:
     *   O__
     *   OO_   (O = Obsidian, S = Stick)
     *   _S_
     */
    public static void registerRecipe(EmpireConquest plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "macuahuitl");
        ItemStack result  = createMacuahuitl(plugin);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("O  ", "OO ", " S ");
        recipe.setIngredient('O', Material.OBSIDIAN);
        recipe.setIngredient('S', Material.STICK);
        plugin.getServer().addRecipe(recipe);
    }

    // ── Identification helper ─────────────────────────────────────────────────

    public static boolean isMacuahuitl(ItemStack item, EmpireConquest plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        String type = item.getItemMeta().getPersistentDataContainer()
            .get(plugin.getItemTypeKey(), PersistentDataType.STRING);
        return "macuahuitl".equals(type);
    }
}
