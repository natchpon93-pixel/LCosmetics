package com.mai.lcosmetics.user;

import com.mai.lcosmetics.cosmetic.Cosmetic;
import com.mai.lcosmetics.cosmetic.CosmeticSlot;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * เก็บ CosmeticUser ของคนที่ออนไลน์ + index จาก entity id
 *
 * <p>index นี้จำเป็นเพราะ packet listener มีแค่ entity id (int) ไม่มี UUID
 * และ {@code Bukkit.getEntity()} ห้ามเรียกจาก netty thread บน Folia
 *
 * <p>{@link #hasAnyEquipmentCosmetic()} คือทางออกเร็วของ packet listener:
 * ถ้าไม่มีใครใส่ cosmetic ช่องเกราะ ก็ไม่ต้องแตะ packet ที่ไหลผ่านเลย
 */
public final class UserManager {

    private final Map<UUID, CosmeticUser> byUuid = new ConcurrentHashMap<>();
    private final Map<Integer, CosmeticUser> byEntityId = new ConcurrentHashMap<>();

    /** นับจำนวน cosmetic ช่องเกราะที่ใส่อยู่ทั้งเซิร์ฟ — อ่านได้จาก netty thread */
    private final AtomicInteger equipmentCosmetics = new AtomicInteger();

    public void add(Player player, CosmeticUser user) {
        byUuid.put(player.getUniqueId(), user);
        byEntityId.put(player.getEntityId(), user);
        recount(user, +1);
    }

    public CosmeticUser remove(Player player) {
        CosmeticUser user = byUuid.remove(player.getUniqueId());
        byEntityId.remove(player.getEntityId());
        if (user != null) recount(user, -1);
        return user;
    }

    public CosmeticUser get(UUID uuid) {
        return byUuid.get(uuid);
    }

    public CosmeticUser get(Player player) {
        return byUuid.get(player.getUniqueId());
    }

    public CosmeticUser byEntityId(int entityId) {
        return byEntityId.get(entityId);
    }

    public java.util.Collection<CosmeticUser> online() {
        return byUuid.values();
    }

    /** เรียกหลังใส่ cosmetic เพื่อให้ตัวนับตรง */
    public void onEquip(Cosmetic cosmetic, Cosmetic replaced) {
        if (cosmetic.slot().isEquipment() && replaced == null) {
            equipmentCosmetics.incrementAndGet();
        }
    }

    /** เรียกหลังถอด cosmetic */
    public void onUnequip(CosmeticSlot slot, Cosmetic removed) {
        if (removed != null && slot.isEquipment()) {
            equipmentCosmetics.decrementAndGet();
        }
    }

    private void recount(CosmeticUser user, int sign) {
        int equipment = 0;
        for (Map.Entry<CosmeticSlot, Cosmetic> entry : user.snapshot().entrySet()) {
            if (entry.getKey().isEquipment()) equipment++;
        }
        if (equipment > 0) equipmentCosmetics.addAndGet(sign * equipment);
    }

    /** ทางออกเร็วของ packet listener — ไม่ล็อกอะไร */
    public boolean hasAnyEquipmentCosmetic() {
        return equipmentCosmetics.get() > 0;
    }

    public void clear() {
        byUuid.clear();
        byEntityId.clear();
        equipmentCosmetics.set(0);
    }
}
