package com.mai.lcosmetics.user;

import com.mai.lcosmetics.render.DisplayRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

/**
 * เชื่อม lifecycle ของผู้เล่นเข้ากับ CosmeticService
 */
public final class PlayerListener implements Listener {

    private final Plugin plugin;
    private final CosmeticService service;
    private final DisplayRenderer displays;

    public PlayerListener(Plugin plugin, CosmeticService service, DisplayRenderer displays) {
        this.plugin = plugin;
        this.service = service;
        this.displays = displays;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuit(event.getPlayer());
    }

    /** ตาย -> display entity ต้องหาย ไม่ให้ค้างกลางอากาศ */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        displays.removeAll(event.getEntity());
    }

    /** เกิดใหม่ -> วาดกลับให้ (หน่วง 1 tick ให้ผู้เล่นลงโลกก่อน) */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;
            CosmeticUser user = service.users().get(player);
            if (user != null) service.renderAll(player, user);
        }, null, 2L);
    }

    /**
     * ย้ายโลก -> ต้อง spawn display ใหม่ในโลกใหม่
     *
     * <p>display entity อยู่ในโลกเดิมและถูกลบไปกับการย้าย นอกจากนั้นชนิด client
     * ของคนในโลกใหม่อาจต่างกัน (Java/Bedrock) จึงต้องวาดใหม่ทั้งชุด
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        displays.removeAll(player);
        player.getScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;
            CosmeticUser user = service.users().get(player);
            if (user != null) service.renderAll(player, user);
        }, null, 2L);
    }

    /** display entity ของเราไม่ควรกินดาเมจหรือบังการโจมตี */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (displays.isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
