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
                sender.sendMessage(Text.parse("<gray><glyph:icon_crown> ใช้คำสั่งนี้ในเกมเท่านั้น"));
                return true;
            }
            new MainMenu(service).open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "menu" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : asPlayer(sender);
                if (target == null) {
                    sender.sendMessage(Text.parse("<red>ไม่พบผู้เล่นคนนั้น"));
                    return true;
                }
                if (target != sender && !sender.hasPermission("lcosmetics.command.menu.other")) {
                    sender.sendMessage(Text.parse("<red>คุณไม่มีสิทธิ์เปิดเมนูให้คนอื่น"));
                    return true;
                }
                new MainMenu(service).open(target);
            }
            case "apply" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.parse("<red>ใช้: /" + label + " apply <id> [ผู้เล่น]"));
                    return true;
                }
                Cosmetic cosmetic = service.registry().get(args[1]);
                if (cosmetic == null) {
                    sender.sendMessage(Text.parse("<red>ไม่พบเครื่องแต่งกาย: " + args[1]));
                    return true;
                }
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : asPlayer(sender);
                if (target == null) {
                    sender.sendMessage(Text.parse("<red>ไม่พบผู้เล่นคนนั้น"));
                    return true;
                }
                if (target != sender && !sender.hasPermission("lcosmetics.command.apply.other")) {
                    sender.sendMessage(Text.parse("<red>คุณไม่มีสิทธิ์ใส่ให้คนอื่น"));
                    return true;
                }
                if (service.equip(target, cosmetic)) {
                    sender.sendMessage(Text.parse("<light_purple><glyph:icon_crown> ใส่ " + cosmetic.displayName() + " <light_purple>ให้ " + target.getName() + " แล้ว"));
                } else {
                    sender.sendMessage(Text.parse("<red>" + target.getName() + " ไม่มีสิทธิ์ใช้ชิ้นนี้"));
                }
            }
            case "unapply" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.parse("<red>ใช้: /" + label + " unapply <ช่อง> [ผู้เล่น]"));
                    return true;
                }
                CosmeticSlot slot = CosmeticSlot.parse(args[1]);
                if (slot == null) {
                    sender.sendMessage(Text.parse("<red>ช่องไม่ถูกต้อง: " + args[1]));
                    return true;
                }
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : asPlayer(sender);
                if (target == null) {
                    sender.sendMessage(Text.parse("<red>ไม่พบผู้เล่นคนนั้น"));
                    return true;
                }
                service.unequip(target, slot);
                sender.sendMessage(Text.parse("<gray>ถอดของช่องนั้นให้ " + target.getName() + " แล้ว"));
            }
            case "clear" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : asPlayer(sender);
                if (target == null) {
                    sender.sendMessage(Text.parse("<red>ไม่พบผู้เล่นคนนั้น"));
                    return true;
                }
                service.unequipAll(target);
                sender.sendMessage(Text.parse("<gray>ถอดทั้งหมดของ " + target.getName() + " แล้ว"));
            }
            case "toggle" -> {
                Player player = asPlayer(sender);
                if (player == null) {
                    sender.sendMessage(Text.parse("<red>ใช้คำสั่งนี้ในเกมเท่านั้น"));
                    return true;
                }
                boolean hidden = service.toggleHidden(player);
                player.sendMessage(Text.parse(hidden
                        ? "<gray>ซ่อนเครื่องแต่งกายแล้ว"
                        : "<light_purple><glyph:icon_crown> แสดงเครื่องแต่งกายแล้ว"));
            }
            case "reload" -> {
                if (!sender.hasPermission("lcosmetics.command.reload")) {
                    sender.sendMessage(Text.parse("<red>คุณไม่มีสิทธิ์ใช้คำสั่งนี้"));
                    return true;
                }
                plugin.reloadEverything();
                sender.sendMessage(Text.parse("<green>โหลด config ใหม่แล้ว <gray>— เครื่องแต่งกาย "
                        + service.registry().size() + " ชิ้น"));
            }
            case "list" -> {
                sender.sendMessage(Text.parse("<light_purple>เครื่องแต่งกายทั้งหมด <gray>(" + service.registry().size() + ")"));
                for (Cosmetic cosmetic : service.registry().all()) {
                    sender.sendMessage(Text.parse("<gray>- <white>" + cosmetic.id()
                            + " <dark_gray>[" + cosmetic.slot().name() + "]"));
                }
            }
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Text.parse("<light_purple><glyph:icon_crown> คำสั่งเครื่องแต่งกาย"));
        sender.sendMessage(Text.parse("<gray>/" + label + " <white>— เปิดเมนู"));
        sender.sendMessage(Text.parse("<gray>/" + label + " toggle <white>— ซ่อน/แสดงของตัวเอง"));
        sender.sendMessage(Text.parse("<gray>/" + label + " apply <id> [ผู้เล่น]"));
        sender.sendMessage(Text.parse("<gray>/" + label + " unapply <ช่อง> [ผู้เล่น]"));
        sender.sendMessage(Text.parse("<gray>/" + label + " clear [ผู้เล่น]"));
        sender.sendMessage(Text.parse("<gray>/" + label + " list"));
        sender.sendMessage(Text.parse("<gray>/" + label + " reload"));
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
