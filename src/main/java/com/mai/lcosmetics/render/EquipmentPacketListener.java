package com.mai.lcosmetics.render;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.mai.lcosmetics.cosmetic.Cosmetic;
import com.mai.lcosmetics.cosmetic.CosmeticSlot;
import com.mai.lcosmetics.user.CosmeticUser;
import com.mai.lcosmetics.user.UserManager;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * แสดง cosmetic ช่องเกราะด้วยการ "แก้ packet ที่เซิร์ฟส่งอยู่แล้ว"
 *
 * <p>ต่างจากวิธีเดิมที่ยิง equipment packet เพิ่มทุกช่วงเวลา: ตัวนี้ดักแค่
 * ENTITY_EQUIPMENT ที่เซิร์ฟกำลังส่งอยู่ดี แล้วสลับ item ก่อนออกจากสาย
 * → ไม่มี packet เพิ่ม ไม่มี timer เดินตลอด และ bandwidth เท่าเดิมเป๊ะ
 *
 * <p>เจ้าตัวไม่โดนแก้ (เกราะจริงยังเห็นในช่องของตัวเอง) — คนอื่นเห็น cosmetic
 */
public final class EquipmentPacketListener extends PacketListenerAbstract {

    private final UserManager users;

    public EquipmentPacketListener(UserManager users) {
        // LOW = ให้ plugin อื่นที่อ่าน packet ดิบทำงานก่อน แล้วเราค่อยทับตอนท้าย
        super(PacketListenerPriority.LOW);
        this.users = users;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_EQUIPMENT) return;

        // ทางออกเร็วสุด: ไม่มีใครใส่ cosmetic ช่องเกราะเลย -> ไม่ต้องแตะ packet
        if (!users.hasAnyEquipmentCosmetic()) return;

        WrapperPlayServerEntityEquipment packet = new WrapperPlayServerEntityEquipment(event);
        int entityId = packet.getEntityId();

        CosmeticUser owner = users.byEntityId(entityId);
        if (owner == null || owner.isHidden()) return;

        // เจ้าตัวเห็นของจริงของตัวเอง
        if (event.getUser() != null && owner.uuid().equals(event.getUser().getUUID())) return;

        Map<CosmeticSlot, Cosmetic> worn = owner.snapshot();
        if (worn.isEmpty()) return;

        List<Equipment> original = packet.getEquipment();
        List<Equipment> rewritten = new ArrayList<>(original.size());
        boolean changed = false;

        for (Equipment slotItem : original) {
            CosmeticSlot mapped = toCosmeticSlot(slotItem.getSlot());
            Cosmetic cosmetic = mapped == null ? null : worn.get(mapped);
            if (cosmetic == null) {
                rewritten.add(slotItem);
                continue;
            }
            ItemStack visual = cosmetic.visual();
            if (visual == null) {
                rewritten.add(slotItem);
                continue;
            }
            rewritten.add(new Equipment(
                    slotItem.getSlot(),
                    SpigotConversionUtil.fromBukkitItemStack(visual)));
            changed = true;
        }

        if (changed) {
            packet.setEquipment(rewritten);
            event.markForReEncode(true);
        }
    }

    private static CosmeticSlot toCosmeticSlot(EquipmentSlot slot) {
        if (slot == EquipmentSlot.HELMET) return CosmeticSlot.HELMET;
        if (slot == EquipmentSlot.CHEST_PLATE) return CosmeticSlot.CHESTPLATE;
        if (slot == EquipmentSlot.LEGGINGS) return CosmeticSlot.LEGGINGS;
        if (slot == EquipmentSlot.BOOTS) return CosmeticSlot.BOOTS;
        if (slot == EquipmentSlot.OFF_HAND) return CosmeticSlot.OFFHAND;
        return null;
    }

    /**
     * บังคับให้คนอื่นเห็น equipment ชุดใหม่ทันทีหลังใส่/ถอด cosmetic
     *
     * <p>ยิงครั้งเดียวต่อการเปลี่ยน ไม่ใช่ทุก tick
     */
    public static void refresh(Player player) {
        List<Equipment> real = List.of(
                new Equipment(EquipmentSlot.HELMET,
                        SpigotConversionUtil.fromBukkitItemStack(safe(player.getInventory().getHelmet()))),
                new Equipment(EquipmentSlot.CHEST_PLATE,
                        SpigotConversionUtil.fromBukkitItemStack(safe(player.getInventory().getChestplate()))),
                new Equipment(EquipmentSlot.LEGGINGS,
                        SpigotConversionUtil.fromBukkitItemStack(safe(player.getInventory().getLeggings()))),
                new Equipment(EquipmentSlot.BOOTS,
                        SpigotConversionUtil.fromBukkitItemStack(safe(player.getInventory().getBoots()))),
                new Equipment(EquipmentSlot.OFF_HAND,
                        SpigotConversionUtil.fromBukkitItemStack(safe(player.getInventory().getItemInOffHand()))));

        WrapperPlayServerEntityEquipment packet =
                new WrapperPlayServerEntityEquipment(player.getEntityId(), real);

        // ส่งให้คนที่มองเห็นเท่านั้น — listener ด้านบนจะสลับเป็น cosmetic ให้เอง
        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) continue;
            if (!viewer.canSee(player)) continue;
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        }
    }

    private static ItemStack safe(ItemStack stack) {
        return stack == null ? new ItemStack(org.bukkit.Material.AIR) : stack;
    }
}
