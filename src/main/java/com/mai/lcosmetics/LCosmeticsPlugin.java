package com.mai.lcosmetics;

import com.github.retrooper.packetevents.PacketEvents;
import com.mai.lcosmetics.command.CosmeticsCommand;
import com.mai.lcosmetics.cosmetic.CosmeticRegistry;
import com.mai.lcosmetics.gui.MenuListener;
import com.mai.lcosmetics.hook.NexoHook;
import com.mai.lcosmetics.render.DisplayRenderer;
import com.mai.lcosmetics.render.EquipmentPacketListener;
import com.mai.lcosmetics.storage.PlayerDataStore;
import com.mai.lcosmetics.user.CosmeticService;
import com.mai.lcosmetics.user.CosmeticUser;
import com.mai.lcosmetics.user.PlayerListener;
import com.mai.lcosmetics.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * LCosmetics — ระบบเครื่องแต่งกายสำหรับ Folia
 *
 * <p>ออกแบบรอบข้อจำกัดของ Folia ตั้งแต่ต้น: ไม่มี global tick task,
 * การแตะ entity ทุกครั้งวิ่งผ่าน entity scheduler ของผู้เล่นคนนั้น,
 * และงานดิสก์อยู่บน async scheduler เสมอ
 */
public final class LCosmeticsPlugin extends JavaPlugin {

    private NexoHook nexo;
    private CosmeticRegistry registry;
    private UserManager users;
    private PlayerDataStore store;
    private DisplayRenderer displays;
    private CosmeticService service;
    private EquipmentPacketListener packetListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("cosmetics.yml");

        if (getServer().getPluginManager().getPlugin("packetevents") == null) {
            getLogger().severe("[LCosmetics] ต้องมี packetevents จึงจะทำงานได้ — ปิด plugin");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.nexo = new NexoHook(getLogger());
        this.registry = new CosmeticRegistry(getLogger(), nexo);
        this.users = new UserManager();
        this.store = new PlayerDataStore(new File(getDataFolder(), "playerdata"), getLogger());
        this.displays = new DisplayRenderer(this);
        this.service = new CosmeticService(this, users, registry, store, displays);

        registry.load(new File(getDataFolder(), "cosmetics.yml"), nexo.isAvailable());

        this.packetListener = new EquipmentPacketListener(users);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);

        getServer().getPluginManager().registerEvents(
                new PlayerListener(this, service, displays), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        hookNexoReload();

        CosmeticsCommand command = new CosmeticsCommand(this, service);
        var registered = getCommand("lcosmetics");
        if (registered != null) {
            registered.setExecutor(command);
            registered.setTabCompleter(command);
        }

        startAutosave();

        // reload ระหว่างเซิร์ฟรันอยู่ -> โหลดคนที่ออนไลน์อยู่แล้วกลับเข้าระบบ
        for (Player player : getServer().getOnlinePlayers()) {
            service.handleJoin(player);
        }

        getLogger().info("[LCosmetics] เปิดใช้งานแล้ว v" + getPluginMeta().getVersion());
    }

    @Override
    public void onDisable() {
        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        }
        if (service != null) {
            // ปิดเซิร์ฟ -> เซฟแบบ sync เพราะ async scheduler จะไม่ได้รันต่อ
            service.saveDirty(false);
            for (Player player : getServer().getOnlinePlayers()) {
                CosmeticUser user = users.get(player);
                if (user != null) store.save(user);
            }
        }
        if (displays != null) displays.shutdown();
        if (users != null) users.clear();
    }

    /**
     * autosave ทุก N วินาที — เซฟแค่คนที่มีการเปลี่ยนแปลงจริง
     *
     * <p>async scheduler ของ Folia ไม่ผูกกับ region ไหน จึงใช้ได้ตรงๆ
     */
    private void startAutosave() {
        long seconds = Math.max(60, getConfig().getLong("autosave-seconds", 300));
        getServer().getAsyncScheduler().runAtFixedRate(this,
                task -> service.saveDirty(false),
                seconds, seconds, TimeUnit.SECONDS);
    }

    /** ใช้โดย /lcosmetics reload */
    public void reloadEverything() {
        reloadConfig();
        registry.load(new File(getDataFolder(), "cosmetics.yml"), false);
        // ของที่ผู้เล่นใส่อยู่อ้างถึง Cosmetic ตัวเก่า -> วาดใหม่ให้ตรง config ใหม่
        for (Player player : getServer().getOnlinePlayers()) {
            service.handleQuit(player);
            service.handleJoin(player);
        }
    }

    /**
     * ผูก NexoItemsLoadedEvent เพื่อโหลด cosmetics ซ้ำเมื่อ Nexo พร้อม
     *
     * <p>Nexo ประกาศ item แบบ async หลังเซิร์ฟขึ้นเสร็จ — plugin เราเปิดก่อนหน้านั้น
     * จึงต้องรอ event นี้แล้วค่อยอ่าน config รอบจริง ไม่ใช่เดาเวลาด้วย delay
     */
    private void hookNexoReload() {
        if (!nexo.isAvailable()) {
            // ไม่มี Nexo -> รอบแรกคือรอบจริงอยู่แล้ว แค่ log ผลออกมา
            logLoadResult();
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(
                    new com.mai.lcosmetics.hook.NexoReloadListener(this::onNexoItemsReady), this);
        } catch (Throwable t) {
            getLogger().warning("[LCosmetics] ผูก NexoItemsLoadedEvent ไม่ได้: " + t.getMessage()
                    + " — ใช้ /cos reload หลังเซิร์ฟขึ้นเสร็จแทน");
            logLoadResult();
        }
    }

    /** Nexo ประกาศ item ครบแล้ว -> โหลด cosmetics ใหม่ให้เห็นของจริง */
    private void onNexoItemsReady() {
        registry.load(new File(getDataFolder(), "cosmetics.yml"), false);
        logLoadResult();
        // คนที่ออนไลน์อยู่ต้องได้ cosmetic ที่เพิ่ง resolve ได้
        for (Player player : getServer().getOnlinePlayers()) {
            CosmeticUser user = users.get(player);
            if (user == null) continue;
            player.getScheduler().run(this, task -> {
                if (player.isOnline()) service.renderAll(player, user);
            }, null);
        }
    }

    private void logLoadResult() {
        getLogger().info("[LCosmetics] พร้อมใช้งาน — เครื่องแต่งกาย " + registry.size() + " ชิ้น"
                + (nexo.isAvailable() ? ", เชื่อมต่อ Nexo แล้ว" : ", ไม่พบ Nexo (ใช้ได้แค่ item vanilla)"));
    }

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }
}
