package com.mai.lcosmetics.cosmetic;

import org.bukkit.inventory.ItemStack;

/**
 * นิยาม cosmetic หนึ่งชิ้นที่อ่านมาจาก cosmetics.yml — immutable
 */
public final class Cosmetic {

    private final String id;
    private final CosmeticSlot slot;
    private final String permission;
    private final String displayName;
    private final boolean dyeable;
    /** item ที่ผู้เล่นคนอื่นเห็น */
    private final ItemStack visual;
    /** item ที่เจ้าตัวเห็น — null = ใช้ visual */
    private final ItemStack firstPerson;

    public Cosmetic(String id,
                    CosmeticSlot slot,
                    String permission,
                    String displayName,
                    boolean dyeable,
                    ItemStack visual,
                    ItemStack firstPerson) {
        this.id = id;
        this.slot = slot;
        this.permission = permission;
        this.displayName = displayName;
        this.dyeable = dyeable;
        this.visual = visual;
        this.firstPerson = firstPerson;
    }

    public String id() {
        return id;
    }

    public CosmeticSlot slot() {
        return slot;
    }

    public String permission() {
        return permission;
    }

    public String displayName() {
        return displayName;
    }

    public boolean dyeable() {
        return dyeable;
    }

    /** clone ทุกครั้ง — กัน caller แก้ของกลาง */
    public ItemStack visual() {
        return visual == null ? null : visual.clone();
    }

    public ItemStack firstPerson() {
        ItemStack base = firstPerson != null ? firstPerson : visual;
        return base == null ? null : base.clone();
    }

    /** ว่างเปล่า = ทุกคนใช้ได้ */
    public boolean isFree() {
        return permission == null || permission.isBlank();
    }
}
