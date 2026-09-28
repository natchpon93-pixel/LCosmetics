package com.mai.lcosmetics.user;

import com.mai.lcosmetics.cosmetic.Cosmetic;
import com.mai.lcosmetics.cosmetic.CosmeticSlot;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * สถานะ cosmetic ของผู้เล่นหนึ่งคนระหว่างอยู่ในเซิร์ฟ
 *
 * <p>อ่าน/เขียนจากหลาย region thread ได้ (Folia) — ใช้ synchronized บน map
 * ซึ่งถูกแตะแค่ตอนใส่/ถอด ไม่ใช่ทุก tick จึงไม่เป็นคอขวด
 */
public final class CosmeticUser {

    private final UUID uuid;
    private final Map<CosmeticSlot, Cosmetic> worn = new EnumMap<>(CosmeticSlot.class);
    private final Map<CosmeticSlot, Integer> dyeColors = new EnumMap<>(CosmeticSlot.class);

    /** ปิด cosmetic ชั่วคราว (เข้า vanish / ใช้ /lc toggle) */
    private final AtomicBoolean hidden = new AtomicBoolean(false);
    /** true = มีการเปลี่ยนที่ยังไม่เซฟลงดิสก์ */
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public CosmeticUser(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public Cosmetic get(CosmeticSlot slot) {
        synchronized (worn) {
            return worn.get(slot);
        }
    }

    /** @return cosmetic ตัวก่อนหน้าในช่องนั้น (null ถ้าว่าง) */
    public Cosmetic put(Cosmetic cosmetic) {
        dirty.set(true);
        synchronized (worn) {
            return worn.put(cosmetic.slot(), cosmetic);
        }
    }

    public Cosmetic remove(CosmeticSlot slot) {
        dirty.set(true);
        synchronized (worn) {
            dyeColors.remove(slot);
            return worn.remove(slot);
        }
    }

    public void clear() {
        dirty.set(true);
        synchronized (worn) {
            worn.clear();
            dyeColors.clear();
        }
    }

    /** snapshot สำหรับ render / save — คัดลอกออกมาเพื่อไม่ถือ lock ข้ามงาน */
    public Map<CosmeticSlot, Cosmetic> snapshot() {
        synchronized (worn) {
            return new EnumMap<>(worn);
        }
    }

    public boolean isEmpty() {
        synchronized (worn) {
            return worn.isEmpty();
        }
    }

    public Integer dye(CosmeticSlot slot) {
        synchronized (worn) {
            return dyeColors.get(slot);
        }
    }

    public void setDye(CosmeticSlot slot, Integer rgb) {
        dirty.set(true);
        synchronized (worn) {
            if (rgb == null) {
                dyeColors.remove(slot);
            } else {
                dyeColors.put(slot, rgb);
            }
        }
    }

    public Map<CosmeticSlot, Integer> dyeSnapshot() {
        synchronized (worn) {
            return new EnumMap<>(dyeColors);
        }
    }

    public boolean isHidden() {
        return hidden.get();
    }

    public void setHidden(boolean value) {
        hidden.set(value);
    }

    /** สลับสถานะและคืนค่าใหม่ */
    public boolean toggleHidden() {
        // AtomicBoolean ไม่มี updateAndGet — วน CAS จนสำเร็จ
        while (true) {
            boolean current = hidden.get();
            if (hidden.compareAndSet(current, !current)) {
                return !current;
            }
        }
    }

    /** ดึงและเคลียร์ธง dirty ในการเรียกครั้งเดียว */
    public boolean consumeDirty() {
        return dirty.getAndSet(false);
    }

    public void markDirty() {
        dirty.set(true);
    }
}
