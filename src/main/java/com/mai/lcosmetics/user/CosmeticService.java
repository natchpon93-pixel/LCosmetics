package com.mai.lcosmetics.user;

import com.mai.lcosmetics.cosmetic.Cosmetic;
import com.mai.lcosmetics.cosmetic.CosmeticRegistry;
import com.mai.lcosmetics.cosmetic.CosmeticSlot;
import com.mai.lcosmetics.render.DisplayRenderer;
import com.mai.lcosmetics.render.EquipmentPacketListener;
import com.mai.lcosmetics.storage.PlayerDataStore;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * ชั้นที่รวมทุกการกระทำกับ cosmetic ไว้ที่เดียว
 *
 * <p>เหตุผลที่แยกออกมา: การใส่ cosmetic หนึ่งครั้งต้องแตะ 3 ที่ (state, packet,
 * display entity) และแต่ละที่มีข้อกำหนด thread ต่างกันบน Folia
 * รวมไว้ที่นี่ทำให้ไม่มีโค้ดไหนลืมขั้นตอนใดไป
 */
public final class CosmeticService {

    private final Plugin plugin;
    private final UserManager users;
    private final CosmeticRegistry registry;
    private final PlayerDataStore store;
    private final DisplayRenderer displays;

    public CosmeticService(Plugin plugin,
                           UserManager users,
                           CosmeticRegistry registry,
                           PlayerDataStore store,
                           DisplayRenderer displays) {
        this.plugin = plugin;
        this.users = users;
        this.registry = registry;
        this.store = store;
        this.displays = displays;
    }

    /** ผู้เล่นเข้าเซิร์ฟ — โหลด async แล้วค่อย render บน region thread */
    public void handleJoin(Player player) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            CosmeticUser user = store.load(player.getUniqueId(), registry);
            // กลับมา region thread ของผู้เล่นเพื่อแตะ entity ได้
            player.getScheduler().run(plugin, t -> {
                if (!player.isOnline()) return;
                users.add(player, user);
                renderAll(player, user);
            }, null);
        });
    }

    /** ผู้เล่นออก — เก็บ entity ทันที แล้วเซฟ async */
    public void handleQuit(Player player) {
        CosmeticUser user = users.remove(player);
        displays.removeAll(player);
        if (user == null) return;
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> store.save(user));
    }

    /**
     * ใส่ cosmetic
     *
     * @return false ถ้าไม่มีสิทธิ์
     */
    public boolean equip(Player player, Cosmetic cosmetic) {
        if (!canUse(player, cosmetic)) return false;
        CosmeticUser user = users.get(player);
        if (user == null) return false;

        Cosmetic replaced = user.put(cosmetic);
        users.onEquip(cosmetic, replaced);

        if (cosmetic.slot().isEquipment()) {
            EquipmentPacketListener.refresh(player);
        } else {
            displays.apply(player, cosmetic.slot(), cosmetic);
        }
        return true;
    }

    /** ถอด cosmetic ช่องเดียว */
    public void unequip(Player player, CosmeticSlot slot) {
        CosmeticUser user = users.get(player);
        if (user == null) return;

        Cosmetic removed = user.remove(slot);
        if (removed == null) return;
        users.onUnequip(slot, removed);

        if (slot.isEquipment()) {
            EquipmentPacketListener.refresh(player);
        } else {
            displays.remove(player, slot);
        }
    }

    /** ถอดทุกชิ้น */
    public void unequipAll(Player player) {
        CosmeticUser user = users.get(player);
        if (user == null) return;

        Map<CosmeticSlot, Cosmetic> worn = user.snapshot();
        if (worn.isEmpty()) return;

        worn.forEach((slot, cosmetic) -> users.onUnequip(slot, cosmetic));
        user.clear();

        displays.removeAll(player);
        EquipmentPacketListener.refresh(player);
    }

    /** สลับซ่อน/แสดง cosmetic ทั้งหมดของตัวเอง */
    public boolean toggleHidden(Player player) {
        CosmeticUser user = users.get(player);
        if (user == null) return false;

        boolean nowHidden = user.toggleHidden();
        if (nowHidden) {
            displays.removeAll(player);
        } else {
            renderAll(player, user);
        }
        EquipmentPacketListener.refresh(player);
        return nowHidden;
    }

    /** วาด cosmetic ทั้งหมดใหม่ — ต้องอยู่บน region thread ของผู้เล่น */
    public void renderAll(Player player, CosmeticUser user) {
        if (user.isHidden()) return;
        user.snapshot().forEach((slot, cosmetic) -> {
            if (!slot.isEquipment()) {
                displays.apply(player, slot, cosmetic);
            }
        });
        if (!user.isEmpty()) {
            EquipmentPacketListener.refresh(player);
        }
    }

    public boolean canUse(Player player, Cosmetic cosmetic) {
        return cosmetic.isFree() || player.hasPermission(cosmetic.permission());
    }

    /** เซฟทุกคนที่มีการเปลี่ยนแปลง — ใช้กับ autosave และตอน disable */
    public void saveDirty(boolean async) {
        for (CosmeticUser user : users.online()) {
            if (!user.consumeDirty()) continue;
            if (async) {
                plugin.getServer().getAsyncScheduler().runNow(plugin, task -> store.save(user));
            } else {
                store.save(user);
            }
        }
    }

    public CosmeticRegistry registry() {
        return registry;
    }

    public UserManager users() {
        return users;
    }

    public DisplayRenderer displays() {
        return displays;
    }
}
