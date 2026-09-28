package com.mai.lcosmetics.storage;

import com.mai.lcosmetics.cosmetic.Cosmetic;
import com.mai.lcosmetics.cosmetic.CosmeticRegistry;
import com.mai.lcosmetics.cosmetic.CosmeticSlot;
import com.mai.lcosmetics.user.CosmeticUser;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * เซฟสถานะผู้เล่นเป็นไฟล์ YAML ไฟล์เดียวต่อคน (playerdata/&lt;uuid&gt;.yml)
 *
 * <p>เลือกวิธีนี้เพราะไม่ต้องพึ่ง database ภายนอก และการเขียนเกิดแค่ตอน
 * ผู้เล่นออก/เปลี่ยน cosmetic — ไม่ใช่ทุก tick จึงไม่กิน I/O
 *
 * <p>ทุก method ที่แตะดิสก์ต้องถูกเรียกจาก async thread (ดู CosmeticService)
 */
public final class PlayerDataStore {

    private final File folder;
    private final Logger log;

    public PlayerDataStore(File folder, Logger log) {
        this.folder = folder;
        this.log = log;
        if (!folder.exists() && !folder.mkdirs()) {
            log.warning("[LCosmetics] สร้างโฟลเดอร์ playerdata ไม่ได้: " + folder);
        }
    }

    private File fileOf(UUID uuid) {
        return new File(folder, uuid + ".yml");
    }

    /** อ่านข้อมูลผู้เล่นจากดิสก์ — เรียกจาก async thread */
    public CosmeticUser load(UUID uuid, CosmeticRegistry registry) {
        CosmeticUser user = new CosmeticUser(uuid);
        File file = fileOf(uuid);
        if (!file.exists()) return user;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        user.setHidden(yaml.getBoolean("hidden", false));

        for (CosmeticSlot slot : CosmeticSlot.values()) {
            String id = yaml.getString("worn." + slot.name());
            if (id == null) continue;
            Cosmetic cosmetic = registry.get(id);
            if (cosmetic == null) {
                // cosmetic ถูกลบออกจาก config ไปแล้ว — ข้ามเงียบๆ ไม่ต้องทำให้ผู้เล่นสะดุด
                continue;
            }
            if (cosmetic.slot() != slot) continue; // config ย้าย slot ไปแล้ว
            user.put(cosmetic);

            int rgb = yaml.getInt("dye." + slot.name(), -1);
            if (rgb >= 0) user.setDye(slot, rgb);
        }
        // เพิ่งโหลดมา = ตรงกับดิสก์อยู่แล้ว
        user.consumeDirty();
        return user;
    }

    /** เขียนลงดิสก์ — เรียกจาก async thread */
    public void save(CosmeticUser user) {
        File file = fileOf(user.uuid());
        Map<CosmeticSlot, Cosmetic> worn = user.snapshot();
        Map<CosmeticSlot, Integer> dyes = user.dyeSnapshot();

        if (worn.isEmpty() && !user.isHidden()) {
            // ไม่มีอะไรต้องจำ — ลบไฟล์ทิ้งกันไฟล์ขยะบวม
            if (file.exists() && !file.delete()) {
                log.warning("[LCosmetics] ลบไฟล์ว่างไม่ได้: " + file.getName());
            }
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("hidden", user.isHidden());
        worn.forEach((slot, cosmetic) -> yaml.set("worn." + slot.name(), cosmetic.id()));
        dyes.forEach((slot, rgb) -> yaml.set("dye." + slot.name(), rgb));

        try {
            yaml.save(file);
        } catch (IOException e) {
            log.warning("[LCosmetics] เซฟข้อมูล " + user.uuid() + " ไม่ได้: " + e.getMessage());
        }
    }

    /** ลบข้อมูลผู้เล่น (คำสั่ง admin) */
    public boolean delete(UUID uuid) {
        File file = fileOf(uuid);
        return !file.exists() || file.delete();
    }
}
