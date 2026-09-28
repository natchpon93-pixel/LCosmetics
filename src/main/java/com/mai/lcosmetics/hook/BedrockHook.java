package com.mai.lcosmetics.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * บอกว่าผู้เล่นคนนี้เข้ามาจาก Bedrock (ผ่าน Geyser/Floodgate) หรือ Java
 *
 * <p>จำเป็นเพราะ Geyser **แปล ItemDisplay ไม่ได้** — ใน jar ของ Geyser
 * มี {@code TextDisplayEntity} แต่ไม่มี {@code ItemDisplayEntity}
 * ผู้เล่น Bedrock จึงมองไม่เห็นเป้/ปีก/ลูกโป่งที่ทำจาก ItemDisplay เลย
 *
 * <p>ทางออกคือส่ง ArmorStand ที่สวมของไว้บนหัวให้ Bedrock แทน
 * ({@code ArmorStandEntity#setHelmet} ถูก Geyser แปลเป็น Bedrock ปกติ)
 * ส่วน Java ยังได้ ItemDisplay ซึ่งวางตำแหน่งได้แม่นกว่า
 *
 * <p>เรียกผ่าน reflection เพื่อให้รันได้แม้เซิร์ฟไม่มี Floodgate
 */
public final class BedrockHook {

    private final boolean available;
    private Object api;
    private Method isFloodgatePlayer;

    public BedrockHook(Logger log) {
        boolean ok = false;
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                this.api = apiClass.getMethod("getInstance").invoke(null);
                this.isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                ok = this.api != null;
            } catch (Throwable t) {
                log.warning("[LCosmetics] พบ Floodgate แต่เชื่อม API ไม่ได้: " + t.getMessage()
                        + " — ผู้เล่น Bedrock จะไม่เห็นเป้/ปีก/ลูกโป่ง");
            }
        }
        this.available = ok;
    }

    public boolean isAvailable() {
        return available;
    }

    /** true = ผู้เล่นคนนี้เล่นจาก Bedrock (มือถือ/คอนโซล) */
    public boolean isBedrock(Player player) {
        if (!available) return false;
        try {
            return (boolean) isFloodgatePlayer.invoke(api, player.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }
}
