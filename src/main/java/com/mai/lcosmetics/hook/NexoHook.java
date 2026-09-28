package com.mai.lcosmetics.hook;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

/**
 * สะพานไป Nexo — เรียกผ่าน reflection เพื่อให้ plugin รันได้แม้เซิร์ฟไม่มี Nexo
 * (compileOnly ทำให้ import ตรงๆ พัง NoClassDefFoundError ตอนไม่มี plugin)
 */
public final class NexoHook {

    private final boolean available;
    private java.lang.reflect.Method itemFromId;
    private final Logger log;

    public NexoHook(Logger log) {
        this.log = log;
        boolean ok = false;
        if (Bukkit.getPluginManager().getPlugin("Nexo") != null) {
            try {
                Class<?> nexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");
                // NexoItems.itemFromId(String) -> ItemBuilder (nullable)
                this.itemFromId = nexoItems.getMethod("itemFromId", String.class);
                ok = true;
            } catch (Throwable t) {
                log.warning("[LCosmetics] พบ Nexo แต่เชื่อม API ไม่ได้: " + t.getMessage());
            }
        }
        this.available = ok;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * แปลง id ของ Nexo เป็น ItemStack
     *
     * @return null ถ้าไม่มี Nexo หรือหา id ไม่เจอ
     */
    public ItemStack build(String id) {
        if (!available || id == null || id.isBlank()) return null;
        try {
            Object builder = itemFromId.invoke(null, id);
            if (builder == null) return null;
            // ItemBuilder#build() -> org.bukkit.inventory.ItemStack
            Object stack = builder.getClass().getMethod("build").invoke(builder);
            return stack instanceof ItemStack is ? is : null;
        } catch (Throwable t) {
            log.warning("[LCosmetics] สร้าง Nexo item '" + id + "' ไม่ได้: " + t.getMessage());
            return null;
        }
    }

    /**
     * อ่านค่า material จาก config — รองรับทั้ง {@code nexo:id} และชื่อ vanilla
     *
     * @param quiet true = ไม่ต้อง log เตือน (ใช้ตอนโหลดรอบแรกที่ Nexo อาจยังไม่พร้อม)
     * @return null ถ้าแปลงไม่ได้
     */
    public ItemStack resolve(String raw, boolean quiet) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        if (value.regionMatches(true, 0, "nexo:", 0, 5)) {
            String id = value.substring(5);
            ItemStack fromNexo = build(id);
            if (fromNexo == null && !quiet) {
                log.warning("[LCosmetics] ไม่พบ Nexo item: " + id);
            }
            return fromNexo;
        }
        Material mat = Material.matchMaterial(value.toUpperCase());
        if (mat == null) {
            if (!quiet) log.warning("[LCosmetics] material ไม่ถูกต้อง: " + value);
            return null;
        }
        return new ItemStack(mat);
    }

    public ItemStack resolve(String raw) {
        return resolve(raw, false);
    }

    /** true ถ้าค่านี้ต้องรอ Nexo โหลดเสร็จก่อนจึงจะ resolve ได้ */
    public static boolean needsNexo(String raw) {
        return raw != null && raw.trim().regionMatches(true, 0, "nexo:", 0, 5);
    }
}
