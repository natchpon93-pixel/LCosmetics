package com.mai.lcosmetics.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

/**
 * กันผู้เล่นหยิบของออกจากเมนู และส่งคลิกไปให้เมนูที่เกี่ยวข้อง
 */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof MainMenu) && !(holder instanceof CosmeticMenu)) return;

        event.setCancelled(true);   // เมนูเป็นแค่ปุ่ม ห้ามขยับของ
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // คลิกในช่องของผู้เล่นเอง -> ไม่ทำอะไร (แต่ยัง cancel ไว้)
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();
        if (holder instanceof MainMenu main) {
            main.handleClick(player, slot);
        } else if (holder instanceof CosmeticMenu menu) {
            menu.handleClick(player, slot, event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MainMenu || holder instanceof CosmeticMenu) {
            event.setCancelled(true);
        }
    }
}
