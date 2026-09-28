package com.mai.lcosmetics.gui;

import com.mai.lcosmetics.cosmetic.Cosmetic;
import com.mai.lcosmetics.cosmetic.CosmeticSlot;
import com.mai.lcosmetics.user.CosmeticService;
import com.mai.lcosmetics.user.CosmeticUser;
import com.mai.lcosmetics.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * เมนูเลือก cosmetic ของช่องหนึ่ง
 *
 * <p>ใช้ InventoryHolder เป็นตัวระบุเมนู แทนการเทียบ title เป็นข้อความ
 * → ไม่พังเวลาเปลี่ยนภาษา และไม่ต้องเก็บ map ผู้เล่นที่เปิดเมนูไว้
 */
public final class CosmeticMenu implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int CONTENT_END = 45;

    private final CosmeticSlot slot;
    private final CosmeticService service;
    private final List<Cosmetic> page = new ArrayList<>();
    private Inventory inventory;

    public CosmeticMenu(CosmeticSlot slot, CosmeticService service) {
        this.slot = slot;
        this.service = service;
    }

    public void open(Player player) {
        List<Cosmetic> options = service.registry().inSlot(slot);
        CosmeticUser user = service.users().get(player);

        inventory = Bukkit.createInventory(this, SIZE, Text.parse(titleFor(slot)));
        page.clear();

        int index = 0;
        for (Cosmetic cosmetic : options) {
            if (index >= CONTENT_END) break;  // หน้าเดียวพอสำหรับ 45 ชิ้น
            boolean owned = service.canUse(player, cosmetic);
            Cosmetic wornNow = user == null ? null : user.get(slot);
            boolean active = wornNow != null && wornNow.id().equals(cosmetic.id());

            inventory.setItem(index, icon(cosmetic, owned, active));
            page.add(cosmetic);
            index++;
        }

        inventory.setItem(49, button(Material.BARRIER, "&cถอดของช่องนี้"));
        player.openInventory(inventory);
    }

    private ItemStack icon(Cosmetic cosmetic, boolean owned, boolean active) {
        ItemStack stack = owned ? cosmetic.visual() : new ItemStack(Material.GRAY_DYE);
        if (stack == null) stack = new ItemStack(Material.GRAY_DYE);

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.parse(cosmetic.displayName()));
            List<String> lore = new ArrayList<>();
            if (!owned) {
                lore.add("&cยังไม่มีสิทธิ์ใช้ชิ้นนี้");
            } else if (active) {
                lore.add("&aใส่อยู่ &7— คลิกเพื่อถอด");
            } else {
                lore.add("&eคลิกเพื่อใส่");
            }
            meta.lore(lore.stream().map(Text::parse).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack button(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.parse(name));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** จัดการคลิก — คืน true ถ้าเมนูนี้กินคลิกไปแล้ว */
    public void handleClick(Player player, int rawSlot, ClickType click) {
        if (rawSlot == 49) {
            service.unequip(player, slot);
            player.sendMessage(Text.parse("&7ถอด " + labelFor(slot) + " แล้ว"));
            open(player);
            return;
        }
        if (rawSlot < 0 || rawSlot >= page.size()) return;

        Cosmetic clicked = page.get(rawSlot);
        CosmeticUser user = service.users().get(player);
        Cosmetic wornNow = user == null ? null : user.get(slot);

        if (wornNow != null && wornNow.id().equals(clicked.id())) {
            service.unequip(player, slot);
            player.sendMessage(Text.parse("&7ถอด " + clicked.displayName() + " &7แล้ว"));
        } else if (service.equip(player, clicked)) {
            player.sendMessage(Text.parse("&aใส่ " + clicked.displayName() + " &aแล้ว"));
        } else {
            player.sendMessage(Text.parse("&cคุณยังไม่มีสิทธิ์ใช้ชิ้นนี้"));
            return;
        }
        open(player);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static String titleFor(CosmeticSlot slot) {
        return "&8เครื่องแต่งกาย — " + labelFor(slot);
    }

    public static String labelFor(CosmeticSlot slot) {
        return switch (slot) {
            case HELMET -> "หมวก";
            case CHESTPLATE -> "เสื้อ";
            case LEGGINGS -> "กางเกง";
            case BOOTS -> "รองเท้า";
            case OFFHAND -> "ของถือมือซ้าย";
            case BACKPACK -> "เป้/ปีก";
            case BALLOON -> "ลูกโป่ง";
        };
    }
}
