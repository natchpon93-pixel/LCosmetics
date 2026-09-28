package com.mai.lcosmetics.command;

import com.mai.lcosmetics.LCosmeticsPlugin;
import com.mai.lcosmetics.cosmetic.Cosmetic;
import com.mai.lcosmetics.cosmetic.CosmeticSlot;
import com.mai.lcosmetics.gui.MainMenu;
import com.mai.lcosmetics.user.CosmeticService;
import com.mai.lcosmetics.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * คำสั่ง /lcosmetics (alias /cos)
 */
public final class CosmeticsCommand implements CommandExecutor, TabCompleter {

    private final LCosmeticsPlugin plugin;
    private final CosmeticService service;

    public CosmeticsCommand(LCosmeticsPlugin plugin, CosmeticService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("ใช้คำสั่งนี้ในเกมเท่านั้น");
                return true;
            }
            new MainMenu(service).open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "menu" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : asPlayer(sender);
                if (target == null) {
                    sender.sendMessage(Text.parse("&cไม่พบผู้เล่นคนนั้น"));
                    return true;
                }
                if (target != sender && !sender.hasPermission("lcosmetics.command.menu.other")) {
                    sender.sendMessage(Text.parse("&cคุณไม่มีสิทธิ์เปิดเมนูให้คนอื่น"));
                    return true;
                }
                new MainMenu(service).open(target);
            }
            case "apply" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.parse("&cใช้: /" + label + " apply <id> [ผู้เล่น]"));
                    return true;
                }
                Cosmetic cosmetic = service.registry().get(args[1]);
                if (cosmetic == null) {
                    sender.sendMessage(Text.parse("&cไม่พบเครื่องแต่งกาย: " + args[1]));
                    return true;
                }
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : asPlayer(sender);
                if (target == null) {
                    sender.sendMessage(Text.parse("&cไม่พบผู้เล่นคนนั้น"));
                    return true;
                }
                if (target != sender && !sender.hasPermission("lcosmetics.command.apply.other")) {
                    sender.sendMessage(Text.parse("&cคุณไม่มีสิทธิ์ใส่ให้คนอื่น"));
                    return true;
                }
                if (service.equip(target, cosmetic)) {
                    sender.sendMessage(Text.parse("&aใส่ " + cosmetic.displayName() + " &aให้ " + target.getName() + " แล้ว"));
                } else {
                    sender.sendMessage(Text.parse("&c" + target.getName() + " ไม่มีสิทธิ์ใช้ชิ้นนี้"));
                }
            }
            case "unapply" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.parse("&cใช้: /" + label + " unapply <ช่อง> [ผู้เล่น]"));
                    return true;
                }
                CosmeticSlot slot = CosmeticSlot.parse(args[1]);
                if (slot == null) {
                    sender.sendMessage(Text.parse("&cช่องไม่ถูกต้อง: " + args[1]));
                    return true;
                }
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : asPlayer(sender);
                if (target == null) {
                    sender.sendMessage(Text.parse("&cไม่พบผู้เล่นคนนั้น"));
                    return true;
                }
                service.unequip(target, slot);
                sender.sendMessage(Text.parse("&7ถอดของช่องนั้นให้ " + target.getName() + " แล้ว"));
            }
            case "clear" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : asPlayer(sender);
                if (target == null) {
                    sender.sendMessage(Text.parse("&cไม่พบผู้เล่นคนนั้น"));
                    return true;
                }
                service.unequipAll(target);
                sender.sendMessage(Text.parse("&7ถอดทั้งหมดของ " + target.getName() + " แล้ว"));
            }
            case "toggle" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    sender.sendMessage(Text.parse("&cใช้คำสั่งนี้ในเกมเท่านั้น"));
                    return true;
                }
                boolean hidden = service.toggleHidden(player);
                player.sendMessage(Text.parse(hidden
                        ? "&7ซ่อนเครื่องแต่งกายแล้ว"
                        : "&aแสดงเครื่องแต่งกายแล้ว"));
            }
            case "reload" -> {
                if (!sender.hasPermission("lcosmetics.command.reload")) {
                    sender.sendMessage(Text.parse("&cคุณไม่มีสิทธิ์ใช้คำสั่งนี้"));
                    return true;
                }
                plugin.reloadEverything();
                sender.sendMessage(Text.parse("&aโหลด config ใหม่แล้ว — เครื่องแต่งกาย "
                        + service.registry().size() + " ชิ้น"));
            }
            case "list" -> {
                sender.sendMessage(Text.parse("&eเครื่องแต่งกายทั้งหมด &7(" + service.registry().size() + ")"));
                for (Cosmetic cosmetic : service.registry().all()) {
                    sender.sendMessage(Text.parse("&7- &f" + cosmetic.id()
                            + " &8[" + cosmetic.slot().name() + "]"));
                }
            }
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Text.parse("&e▸ คำสั่งเครื่องแต่งกาย"));
        sender.sendMessage(Text.parse("&7/" + label + " &f— เปิดเมนู"));
        sender.sendMessage(Text.parse("&7/" + label + " toggle &f— ซ่อน/แสดงของตัวเอง"));
        sender.sendMessage(Text.parse("&7/" + label + " apply <id> [ผู้เล่น]"));
        sender.sendMessage(Text.parse("&7/" + label + " unapply <ช่อง> [ผู้เล่น]"));
        sender.sendMessage(Text.parse("&7/" + label + " clear [ผู้เล่น]"));
        sender.sendMessage(Text.parse("&7/" + label + " list"));
        sender.sendMessage(Text.parse("&7/" + label + " reload"));
    }

    private Player asPlayer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("menu", "apply", "unapply", "clear", "toggle", "list", "reload")) {
                if (sub.startsWith(args[0].toLowerCase())) out.add(sub);
            }
            return out;
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("apply")) {
                for (Cosmetic cosmetic : service.registry().all()) {
                    if (cosmetic.id().startsWith(args[1].toLowerCase())) out.add(cosmetic.id());
                }
                return out;
            }
            if (args[0].equalsIgnoreCase("unapply")) {
                for (CosmeticSlot slot : CosmeticSlot.values()) {
                    if (slot.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                        out.add(slot.name());
                    }
                }
                return out;
            }
        }
        return out;
    }
}
