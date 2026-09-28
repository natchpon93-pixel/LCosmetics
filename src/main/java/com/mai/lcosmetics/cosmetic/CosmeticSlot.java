package com.mai.lcosmetics.cosmetic;

/**
 * ช่องที่ cosmetic ใส่ได้
 *
 * <p>HELMET / CHESTPLATE / LEGGINGS / BOOTS / OFFHAND — ส่งผ่าน equipment packet
 * ทับของจริงที่ผู้เล่นใส่ (คนอื่นเห็น cosmetic แต่เกราะจริงยังทำงานปกติ)
 *
 * <p>BACKPACK / BALLOON — spawn เป็น display entity ตามตัวผู้เล่น
 */
public enum CosmeticSlot {
    HELMET(true),
    CHESTPLATE(true),
    LEGGINGS(true),
    BOOTS(true),
    OFFHAND(true),
    BACKPACK(false),
    BALLOON(false);

    private final boolean equipment;

    CosmeticSlot(boolean equipment) {
        this.equipment = equipment;
    }

    /** true = ส่งผ่าน equipment packet, false = ใช้ display entity */
    public boolean isEquipment() {
        return equipment;
    }

    public static CosmeticSlot parse(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toUpperCase().replace('-', '_');
        switch (key) {
            case "HAT":
            case "HEAD":
                return HELMET;
            case "CHEST":
                return CHESTPLATE;
            case "PANTS":
            case "LEGS":
                return LEGGINGS;
            case "SHOES":
                return BOOTS;
            case "HAND":
            case "OFF_HAND":
                return OFFHAND;
            case "WINGS":
                return BACKPACK;
            default:
                break;
        }
        try {
            return valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
