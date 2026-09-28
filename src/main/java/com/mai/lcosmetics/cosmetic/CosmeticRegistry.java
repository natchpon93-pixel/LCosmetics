package com.mai.lcosmetics.cosmetic;

import com.mai.lcosmetics.hook.NexoHook;
import com.mai.lcosmetics.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * โหลด cosmetics.yml -> เก็บเป็น registry ที่อ่านได้เร็ว
 *
 * <p>โครงสร้าง config หนึ่งรายการ:
 * <pre>
 * beanie:
 *   slot: HELMET
 *   permission: "lcosmetics.beanie"
 *   dyeable: false
 *   item:
 *     material: nexo:beanie
 *     name: "&bหมวกไหมพรม"
 *     lore: ["&7น่ารัก"]
 *   firstperson-item:
 *     material: nexo:beanie_first
 * </pre>
 */
public final class CosmeticRegistry {

    private final Logger log;
    private final NexoHook nexo;

    /** id -> cosmetic */
    private Map<String, Cosmetic> byId = Collections.emptyMap();
    /** slot -> cosmetics ในช่องนั้น (เรียงตาม config) */
    private Map<CosmeticSlot, List<Cosmetic>> bySlot = Collections.emptyMap();
    /** จำนวนชิ้นที่ข้ามไปเพราะ Nexo ยังไม่พร้อม */
    private int awaitingNexo = 0;
    /** ปิด log เตือนตอนโหลดรอบแรก */
    private boolean quiet = false;

    public CosmeticRegistry(Logger log, NexoHook nexo) {
        this.log = log;
        this.nexo = nexo;
    }

    public void load(File file) {
        load(file, false);
    }

    /**
     * @param quiet true = ไม่ log เตือนตอน resolve ไม่ได้
     *              (ใช้ตอนโหลดรอบแรกที่ Nexo อาจยังไม่ประกาศ item เสร็จ)
     */
    public void load(File file, boolean quiet) {
        this.quiet = quiet;
        Map<String, Cosmetic> ids = new LinkedHashMap<>();
        Map<CosmeticSlot, List<Cosmetic>> slots = new LinkedHashMap<>();
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            slots.put(slot, new ArrayList<>());
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int skipped = 0;
        int pendingNexo = 0;
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection sec = yaml.getConfigurationSection(id);
            if (sec == null) {
                skipped++;
                continue;
            }
            Cosmetic cosmetic = read(id, sec);
            if (cosmetic == null) {
                skipped++;
                ConfigurationSection itemSec = sec.getConfigurationSection("item");
                if (itemSec != null && NexoHook.needsNexo(itemSec.getString("material"))) {
                    pendingNexo++;
                }
                continue;
            }
            ids.put(id.toLowerCase(), cosmetic);
            slots.get(cosmetic.slot()).add(cosmetic);
        }

        this.byId = Collections.unmodifiableMap(ids);
        Map<CosmeticSlot, List<Cosmetic>> frozen = new LinkedHashMap<>();
        slots.forEach((slot, list) -> frozen.put(slot, List.copyOf(list)));
        this.bySlot = Collections.unmodifiableMap(frozen);
        this.awaitingNexo = pendingNexo;

        if (!quiet) {
            log.info("[LCosmetics] โหลด cosmetic " + ids.size() + " ชิ้น"
                    + (skipped > 0 ? " (ข้าม " + skipped + " ชิ้นที่ config ผิด)" : ""));
        }
    }

    /** จำนวนชิ้นที่โหลดไม่ได้เพราะรอ Nexo — ใช้ตัดสินว่าต้องโหลดซ้ำไหม */
    public int awaitingNexo() {
        return awaitingNexo;
    }

    private void warn(String message) {
        if (!quiet) log.warning("[LCosmetics] " + message);
    }

    private Cosmetic read(String id, ConfigurationSection sec) {
        CosmeticSlot slot = CosmeticSlot.parse(sec.getString("slot"));
        if (slot == null) {
            warn("'" + id + "' slot ไม่ถูกต้อง: " + sec.getString("slot"));
            return null;
        }

        ConfigurationSection itemSec = sec.getConfigurationSection("item");
        if (itemSec == null) {
            warn("'" + id + "' ไม่มี section 'item'");
            return null;
        }
        ItemStack visual = buildItem(id, itemSec);
        if (visual == null) return null;

        ItemStack first = null;
        ConfigurationSection firstSec = sec.getConfigurationSection("firstperson-item");
        if (firstSec != null) {
            first = buildItem(id + " (firstperson)", firstSec);
        }

        String name = itemSec.getString("name", id);
        return new Cosmetic(
                id,
                slot,
                sec.getString("permission", ""),
                name,
                sec.getBoolean("dyeable", false),
                visual,
                first);
    }

    private ItemStack buildItem(String label, ConfigurationSection sec) {
        ItemStack stack = nexo.resolve(sec.getString("material"), quiet);
        if (stack == null) {
            warn("'" + label + "' สร้าง item ไม่ได้ ข้ามชิ้นนี้");
            return null;
        }
        stack.setAmount(Math.max(1, sec.getInt("amount", 1)));

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = sec.getString("name");
            if (name != null && !name.isBlank()) {
                meta.displayName(Text.parse(name));
            }
            List<String> lore = sec.getStringList("lore");
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(Text::parse).toList());
            }
            int modelData = sec.getInt("custom-model-data", -1);
            if (modelData >= 0) {
                meta.setCustomModelData(modelData);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public Cosmetic get(String id) {
        return id == null ? null : byId.get(id.toLowerCase());
    }

    public List<Cosmetic> inSlot(CosmeticSlot slot) {
        return bySlot.getOrDefault(slot, List.of());
    }

    public java.util.Collection<Cosmetic> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }
}
