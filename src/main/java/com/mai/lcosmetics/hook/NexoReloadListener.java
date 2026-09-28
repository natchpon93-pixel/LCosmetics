package com.mai.lcosmetics.hook;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * รอสัญญาณจาก Nexo ว่าประกาศ item ครบแล้ว
 *
 * <p>เหตุผลที่ต้องมี: Nexo โหลด item ของตัวเองแบบ async หลังเซิร์ฟขึ้นเสร็จ
 * ส่วน plugin ที่ {@code depend} กัน จะ enable ก่อนหน้านั้น
 * ถ้าอ่าน config ตอน onEnable ตรงๆ จะหา Nexo item ไม่เจอทุกตัว
 *
 * <p>ผูก event ด้วย reflection-safe pattern: คลาสนี้ถูกโหลดเฉพาะเมื่อมี Nexo
 * (ดู {@code LCosmeticsPlugin#hookNexoReload})
 */
public final class NexoReloadListener implements Listener {

    private final Runnable onItemsLoaded;

    public NexoReloadListener(Runnable onItemsLoaded) {
        this.onItemsLoaded = onItemsLoaded;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNexoItemsLoaded(com.nexomc.nexo.api.events.NexoItemsLoadedEvent event) {
        onItemsLoaded.run();
    }
}
