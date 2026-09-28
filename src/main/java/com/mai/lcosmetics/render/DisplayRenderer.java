package com.mai.lcosmetics.render;

import com.mai.lcosmetics.cosmetic.Cosmetic;
import com.mai.lcosmetics.cosmetic.CosmeticSlot;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * แสดง cosmetic ช่อง BACKPACK / BALLOON ด้วย ItemDisplay ที่ "นั่ง" บนผู้เล่น
 *
 * <p>จุดที่เบากว่าวิธีเดิมอย่างมีนัยสำคัญ: ใช้ {@code addPassenger} ให้ client
 * ผูกตำแหน่งเอง แทนการตั้ง repeating task ย้ายตำแหน่ง entity ทุก tick
 * → ไม่มี task เดินเลยสำหรับ cosmetic ประเภทนี้ และไม่มี teleport packet ไหล
 *
 * <p>Folia: การ spawn/remove entity ต้องทำบน region thread ของผู้เล่น
 * ทุก method ในคลาสนี้จึงต้องถูกเรียกผ่าน entity scheduler ของผู้เล่นเท่านั้น
 */
public final class DisplayRenderer {

    /** ใช้ทำเครื่องหมายว่า entity นี้เป็นของเรา — กันชนกับ entity ผู้เล่นสร้าง */
    private final NamespacedKey markerKey;
    private final Plugin plugin;

    /** uuid ผู้เล่น -> (slot -> entity uuid) */
    private final Map<UUID, Map<CosmeticSlot, UUID>> spawned = new ConcurrentHashMap<>();

    public DisplayRenderer(Plugin plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "cosmetic");
    }

    /**
     * spawn / อัปเดต display entity ให้ตรงกับ cosmetic ที่ใส่อยู่
     *
     * <p>ต้องเรียกบน region thread ของผู้เล่น
     */
    public void apply(Player player, CosmeticSlot slot, Cosmetic cosmetic) {
        if (slot.isEquipment()) return;
        remove(player, slot);
        if (cosmetic == null) return;

        Location at = player.getLocation();
        ItemDisplay display = player.getWorld().spawn(at, ItemDisplay.class, entity -> {
            entity.setItemStack(cosmetic.visual());
            entity.setPersistent(false);   // ไม่เขียนลง region file — กัน entity ผีค้างโลก
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setGravity(false);
            // interpolation สั้นๆ ให้ขยับลื่นเวลาผู้เล่นวิ่ง โดยไม่ต้องส่ง packet เพิ่ม
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(2);
            entity.setViewRange(0.6f);     // ไกลกว่านี้ไม่ต้อง render — ลดภาระ client
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setTransformation(transformFor(slot));
            entity.getPersistentDataContainer()
                    .set(markerKey, PersistentDataType.STRING, slot.name());
        });

        // client ผูกตำแหน่งตามพาหนะเอง -> ไม่ต้องมี task ตามทุก tick
        player.addPassenger(display);

        spawned.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(CosmeticSlot.class))
                .put(slot, display.getUniqueId());
    }

    private Transformation transformFor(CosmeticSlot slot) {
        // ตำแหน่งอ้างอิงจากกลางตัวผู้เล่น
        Vector3f translation = slot == CosmeticSlot.BALLOON
                ? new Vector3f(0f, 2.2f, 0f)     // ลอยเหนือหัว
                : new Vector3f(0f, 0.55f, 0.22f); // แนบหลัง
        float scale = slot == CosmeticSlot.BALLOON ? 1.0f : 0.9f;
        return new Transformation(
                translation,
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf());
    }

    /** ลบ display entity ของช่องนั้น — ต้องเรียกบน region thread ของผู้เล่น */
    public void remove(Player player, CosmeticSlot slot) {
        Map<CosmeticSlot, UUID> mine = spawned.get(player.getUniqueId());
        if (mine == null) return;
        UUID id = mine.remove(slot);
        if (id == null) return;
        Entity entity = org.bukkit.Bukkit.getEntity(id);
        if (entity != null) entity.remove();
        if (mine.isEmpty()) spawned.remove(player.getUniqueId());
    }

    /** ลบทุก display ของผู้เล่น (ออกเซิร์ฟ / ซ่อน cosmetic) */
    public void removeAll(Player player) {
        Map<CosmeticSlot, UUID> mine = spawned.remove(player.getUniqueId());
        if (mine == null) return;
        for (UUID id : mine.values()) {
            Entity entity = org.bukkit.Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
    }

    /** เคลียร์ทั้งหมดตอน plugin disable */
    public void shutdown() {
        for (Map<CosmeticSlot, UUID> mine : spawned.values()) {
            for (UUID id : mine.values()) {
                Entity entity = org.bukkit.Bukkit.getEntity(id);
                if (entity != null) entity.remove();
            }
        }
        spawned.clear();
    }

    /**
     * เก็บกวาด display entity ที่ค้างจากการ crash รอบก่อน
     *
     * <p>persistent=false ทำให้ไม่ควรมีค้าง แต่เช็กไว้กันเหนียว
     */
    public boolean isOurs(Entity entity) {
        return entity.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING);
    }

    public NamespacedKey markerKey() {
        return markerKey;
    }
}
