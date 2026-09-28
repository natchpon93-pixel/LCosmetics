package com.mai.lcosmetics.render;

import com.mai.lcosmetics.cosmetic.Cosmetic;
import com.mai.lcosmetics.cosmetic.CosmeticSlot;
import com.mai.lcosmetics.hook.BedrockHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * แสดง cosmetic ช่อง BACKPACK / BALLOON ให้เห็นทั้ง Java และ Bedrock
 *
 * <p><b>ทำไมต้องมี 2 ร่าง:</b> Geyser แปล {@code ItemDisplay} ไม่ได้
 * (ใน jar ของ Geyser มี {@code TextDisplayEntity} แต่ไม่มี {@code ItemDisplayEntity})
 * ผู้เล่น Bedrock จึงมองไม่เห็น ItemDisplay เลย
 * แต่ Geyser แปล {@code ArmorStandEntity} ได้ รวมถึง {@code setHelmet()}
 * ⇒ สวม cosmetic ไว้ช่องหัวของ ArmorStand ล่องหน Bedrock ก็เห็นโมเดล Nexo ตัวเดียวกัน
 *
 * <p>ทั้งสองร่างตั้ง {@code setVisibleByDefault(false)} แล้วค่อย {@code showEntity}
 * ให้เฉพาะกลุ่มที่ถูกต้อง ⇒ ไม่มีใครเห็นซ้อนกัน
 *
 * <p><b>ประหยัดทรัพยากร:</b> ArmorStand ถูก spawn เฉพาะเมื่อมีผู้เล่น Bedrock
 * อยู่ในโลกนั้นจริง — เซิร์ฟที่มีแต่ผู้เล่น Java จะไม่มี entity ส่วนเกินเลย
 *
 * <p>ใช้ {@code addPassenger} ทั้งคู่ ⇒ client คำนวณตำแหน่งเอง ไม่มี task ตามทุก tick
 *
 * <p>Folia: ทุก method ต้องถูกเรียกบน region thread ของผู้เล่นคนนั้น
 */
public final class DisplayRenderer {

    /** ใช้ทำเครื่องหมายว่า entity นี้เป็นของเรา — กันชนกับ entity ผู้เล่นสร้าง */
    private final NamespacedKey markerKey;
    private final Plugin plugin;
    private final BedrockHook bedrock;

    /** uuid ผู้เล่น -> (slot -> entity uuid ที่ spawn ไว้ อาจมี 2 ร่างต่อ slot) */
    private final Map<UUID, Map<CosmeticSlot, List<UUID>>> spawned = new ConcurrentHashMap<>();

    public DisplayRenderer(Plugin plugin, BedrockHook bedrock) {
        this.plugin = plugin;
        this.bedrock = bedrock;
        this.markerKey = new NamespacedKey(plugin, "cosmetic");
    }

    /**
     * spawn / อัปเดต cosmetic ของช่องนั้น
     *
     * <p>ต้องเรียกบน region thread ของผู้เล่น
     */
    public void apply(Player player, CosmeticSlot slot, Cosmetic cosmetic) {
        if (slot.isEquipment()) return;
        remove(player, slot);
        if (cosmetic == null) return;

        ItemStack visual = cosmetic.visual();
        if (visual == null) return;

        List<UUID> created = new ArrayList<>(2);

        // ── ร่างสำหรับ Java: ItemDisplay วางตำแหน่งได้แม่นกว่า ──
        List<Player> javaViewers = viewers(player, false);
        if (!javaViewers.isEmpty()) {
            ItemDisplay display = spawnItemDisplay(player, slot, visual);
            player.addPassenger(display);
            for (Player viewer : javaViewers) {
                viewer.showEntity(plugin, display);
            }
            created.add(display.getUniqueId());
        }

        // ── ร่างสำหรับ Bedrock: ArmorStand ล่องหนสวมของไว้บนหัว ──
        // spawn เฉพาะเมื่อมีคน Bedrock อยู่จริง เพื่อไม่ให้เซิร์ฟ Java-only แบก entity เปล่า
        List<Player> bedrockViewers = viewers(player, true);
        if (!bedrockViewers.isEmpty()) {
            ArmorStand stand = spawnArmorStand(player, slot, visual);
            player.addPassenger(stand);
            for (Player viewer : bedrockViewers) {
                viewer.showEntity(plugin, stand);
            }
            created.add(stand.getUniqueId());
        }

        if (!created.isEmpty()) {
            spawned.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(CosmeticSlot.class))
                    .put(slot, created);
        }
    }

    private ItemDisplay spawnItemDisplay(Player player, CosmeticSlot slot, ItemStack visual) {
        Location at = player.getLocation();
        return player.getWorld().spawn(at, ItemDisplay.class, entity -> {
            entity.setItemStack(visual);
            entity.setVisibleByDefault(false);  // โชว์เฉพาะคนที่เรา showEntity ให้
            entity.setPersistent(false);        // ไม่เขียนลง region file — กัน entity ผีค้างโลก
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setGravity(false);
            // interpolation สั้นๆ ให้ขยับลื่นเวลาผู้เล่นวิ่ง โดยไม่ต้องส่ง packet เพิ่ม
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(2);
            entity.setViewRange(0.6f);          // ไกลกว่านี้ไม่ต้อง render — ลดภาระ client
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setTransformation(transformFor(slot));
            entity.getPersistentDataContainer()
                    .set(markerKey, PersistentDataType.STRING, slot.name());
        });
    }

    /**
     * ArmorStand ล่องหนแบบ marker (ไม่มี hitbox, ไม่มีฐาน) สวม cosmetic ที่ช่องหัว
     *
     * <p>ข้อจำกัดที่ยอมรับ: ArmorStand ไม่มี Transformation แบบ display entity
     * ตำแหน่งจึงอยู่ที่จุด mount (แถวหัวผู้เล่น) ทั้ง BACKPACK และ BALLOON
     * — ต่างจาก Java ที่ BACKPACK แนบหลังได้พอดี
     * ใช้ {@code setSmall} กับเป้เพื่อให้ขนาดใกล้เคียงฝั่ง Java
     */
    private ArmorStand spawnArmorStand(Player player, CosmeticSlot slot, ItemStack visual) {
        Location at = player.getLocation();
        return player.getWorld().spawn(at, ArmorStand.class, stand -> {
            stand.setVisible(false);            // ตัวหุ่นล่องหน เห็นแต่ของที่สวม
            stand.setMarker(true);              // ไม่มี hitbox ไม่บังการโจมตี
            stand.setSmall(slot == CosmeticSlot.BACKPACK);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setGravity(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setCanPickupItems(false);
            stand.setVisibleByDefault(false);   // โชว์เฉพาะผู้เล่น Bedrock
            stand.setPersistent(false);
            if (stand.getEquipment() != null) {
                stand.getEquipment().setItem(EquipmentSlot.HEAD, visual);
            }
            stand.getPersistentDataContainer()
                    .set(markerKey, PersistentDataType.STRING, slot.name());
        });
    }

    /**
     * คนที่ควรเห็น cosmetic ของ {@code owner}
     *
     * <p>เจ้าตัวก็รวมอยู่ด้วย — เป้/ปีก/ลูกโป่งเป็น cosmetic ที่เจ้าของควรเห็นเอง
     *
     * @param wantBedrock true = เอาเฉพาะผู้เล่น Bedrock, false = เฉพาะ Java
     */
    private List<Player> viewers(Player owner, boolean wantBedrock) {
        List<Player> out = new ArrayList<>();
        for (Player viewer : owner.getWorld().getPlayers()) {
            if (bedrock.isBedrock(viewer) == wantBedrock) {
                out.add(viewer);
            }
        }
        return out;
    }

    private Transformation transformFor(CosmeticSlot slot) {
        // ตำแหน่งอ้างอิงจากกลางตัวผู้เล่น
        Vector3f translation = slot == CosmeticSlot.BALLOON
                ? new Vector3f(0f, 2.2f, 0f)      // ลอยเหนือหัว
                : new Vector3f(0f, 0.55f, 0.22f); // แนบหลัง
        float scale = slot == CosmeticSlot.BALLOON ? 1.0f : 0.9f;
        return new Transformation(
                translation,
                new Quaternionf(),
                new Vector3f(scale, scale, scale),
                new Quaternionf());
    }

    /** ลบ cosmetic ของช่องนั้นทุกร่าง — ต้องเรียกบน region thread ของผู้เล่น */
    public void remove(Player player, CosmeticSlot slot) {
        Map<CosmeticSlot, List<UUID>> mine = spawned.get(player.getUniqueId());
        if (mine == null) return;
        List<UUID> ids = mine.remove(slot);
        if (ids == null) return;
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        if (mine.isEmpty()) spawned.remove(player.getUniqueId());
    }

    /** ลบทุกร่างของผู้เล่น (ออกเซิร์ฟ / ซ่อน cosmetic / ตาย) */
    public void removeAll(Player player) {
        Map<CosmeticSlot, List<UUID>> mine = spawned.remove(player.getUniqueId());
        if (mine == null) return;
        for (List<UUID> ids : mine.values()) {
            for (UUID id : ids) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) entity.remove();
            }
        }
    }

    /** true ถ้าผู้เล่นคนนี้มี cosmetic display อยู่ */
    public boolean hasAny(Player player) {
        Map<CosmeticSlot, List<UUID>> mine = spawned.get(player.getUniqueId());
        return mine != null && !mine.isEmpty();
    }

    /** เคลียร์ทั้งหมดตอน plugin disable */
    public void shutdown() {
        for (Map<CosmeticSlot, List<UUID>> mine : spawned.values()) {
            for (List<UUID> ids : mine.values()) {
                for (UUID id : ids) {
                    Entity entity = Bukkit.getEntity(id);
                    if (entity != null) entity.remove();
                }
            }
        }
        spawned.clear();
    }

    /**
     * เก็บกวาด entity ที่ค้างจากการ crash รอบก่อน
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
