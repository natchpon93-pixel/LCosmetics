package com.mai.lcosmetics.gui;

import com.mai.lcosmetics.cosmetic.CosmeticSlot;
import com.mai.lcosmetics.user.CosmeticService;
import com.mai.lcosmetics.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * เมนูหลัก — เลือกว่าจะแต่งช่องไหน
 */
public final class MainMenu implements InventoryHolder {

    private static final Map<Integer, CosmeticSlot> LAYOUT = new HashMap<>();

    static {
        LAYOUT.put(10, CosmeticSlot.HELMET);
        LAYOUT.put(11, CosmeticSlot.CHESTPLATE);
        LAYOUT.put(12, CosmeticSlot.LEGGINGS);
        LAYOUT.put(13, CosmeticSlot.BOOTS);
        LAYOUT.put(14, CosmeticSlot.OFFHAND);
        LAYOUT.put(15, CosmeticSlot.BACKPACK);
        LAYOUT.put(16, CosmeticSlot.BALLOON);
    }

    private static final Map<CosmeticSlot, Material> ICONS = Map.of(
            CosmeticSlot.HELMET, Material.LEATHER_HELMET,
            CosmeticSlot.CHESTPLATE, Material.LEATHER_CHESTPLATE,
            CosmeticSlot.LEGGINGS, Material.LEATHER_LEGGINGS,
            CosmeticSlot.BOOTS, Material.LEATHER_BOOTS,
            CosmeticSlot.OFFHAND, Material.SHIELD,
            CosmeticSlot.BACKPACK, Material.ELYTRA,
            CosmeticSlot.BALLOON, Material.LEAD);

    private final CosmeticService service;
    private Inventory inventory;

    public MainMenu(CosmeticService service) {
        this.service = service;
    }

    public void open(Player player) {
        inventory = Bukkit.createInventory(this, 27, Text.parse("<dark_purple><glyph:icon_crown> เครื่องแต่งกาย"));

        LAYOUT.forEach((index, slot) -> {
            int count = service.registry().inSlot(slot).size();
            ItemStack stack = new ItemStack(ICONS.getOrDefault(slot, Material.PAPER));
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.displayName(Text.parse("<white>" + CosmeticMenu.labelFor(slot)));
                meta.lore(List.of(
                        Text.parse("<gray>มีให้เลือก <white>" + count + " <gray>ชิ้น"),
                        Text.parse(count > 0 ? "<light_purple>► กดเพื่อเปิด" : "<dark_gray>ยังไม่มีของในช่องนี้")));
                stack.setItemMeta(meta);
            }
            inventory.setItem(index, stack);
        });

        ItemStack clear = new ItemStack(Material.BARRIER);
        ItemMeta clearMeta = clear.getItemMeta();
        if (clearMeta != null) {
            clearMeta.displayName(Text.parse("<red>ถอดทั้งหมด"));
            clear.setItemMeta(clearMeta);
        }
        inventory.setItem(22, clear);

        player.openInventory(inventory);
    }

    public void handleClick(Player player, int rawSlot) {
        if (rawSlot == 22) {
            service.unequipAll(player);
            player.sendMessage(Text.parse("<gray>ถอดเครื่องแต่งกายทั้งหมดแล้ว"));
            open(player);
            return;
        }
        CosmeticSlot slot = LAYOUT.get(rawSlot);
        if (slot == null) return;
        if (service.registry().inSlot(slot).isEmpty()) {
            player.sendMessage(Text.parse("<red>ยังไม่มีของในช่อง " + CosmeticMenu.labelFor(slot)));
            return;
        }
        new CosmeticMenu(slot, service).open(player);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
