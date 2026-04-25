package com.empireconquest.objects;

import com.empireconquest.EmpireConquest;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class MusketItem {

    private MusketItem() {}

    // ── Item creation ─────────────────────────────────────────────────────────

    public static ItemStack createMusket(EmpireConquest plugin) {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§6Musket");
        meta.setLore(List.of("§7A deadly Spanish firearm", "§eLeft-click to fire"));
        meta.getPersistentDataContainer()
            .set(plugin.getItemTypeKey(), PersistentDataType.STRING, "musket");
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createMusketBall(EmpireConquest plugin) {
        ItemStack item = new ItemStack(Material.IRON_NUGGET);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§7Musket Ball");
        meta.getPersistentDataContainer()
            .set(plugin.getItemTypeKey(), PersistentDataType.STRING, "musket_ball");
        item.setItemMeta(meta);
        return item;
    }

    // ── Recipe registration ───────────────────────────────────────────────────

    public static void registerRecipes(EmpireConquest plugin) {
        // Musket: III / IGI / _S_   (I=Iron Ingot, G=Gunpowder, S=Stick)
        NamespacedKey musketKey = new NamespacedKey(plugin, "musket");
        ItemStack musketResult = createMusket(plugin);
        ShapedRecipe musketRecipe = new ShapedRecipe(musketKey, musketResult);
        musketRecipe.shape("III", "IGI", " S ");
        musketRecipe.setIngredient('I', Material.IRON_INGOT);
        musketRecipe.setIngredient('G', Material.GUNPOWDER);
        musketRecipe.setIngredient('S', Material.STICK);
        plugin.getServer().addRecipe(musketRecipe);

        // Musket Ball: 1 Iron Ingot → 4 balls (shapeless)
        NamespacedKey ballKey = new NamespacedKey(plugin, "musket_ball");
        ItemStack ballResult = createMusketBall(plugin);
        ballResult.setAmount(4);
        ShapelessRecipe ballRecipe = new ShapelessRecipe(ballKey, ballResult);
        ballRecipe.addIngredient(Material.IRON_INGOT);
        plugin.getServer().addRecipe(ballRecipe);
    }

    // ── Identification helpers ────────────────────────────────────────────────

    public static boolean isMusket(ItemStack item, EmpireConquest plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        String type = item.getItemMeta().getPersistentDataContainer()
            .get(plugin.getItemTypeKey(), PersistentDataType.STRING);
        return "musket".equals(type);
    }

    public static boolean isMusketBall(ItemStack item, EmpireConquest plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        String type = item.getItemMeta().getPersistentDataContainer()
            .get(plugin.getItemTypeKey(), PersistentDataType.STRING);
        return "musket_ball".equals(type);
    }
}
