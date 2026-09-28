package com.mai.lcosmetics.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * แปลงข้อความ config -> Component
 * รองรับ MiniMessage ({@code <red>}) และโค้ดสีเก่า ({@code &c}) ในไฟล์เดียวกัน
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private Text() {
    }

    public static Component parse(String raw) {
        if (raw == null) return Component.empty();
        String value = raw;
        // มีโค้ดสีเก่า -> แปลงเป็น MiniMessage ก่อน เพื่อให้ผสมกันได้
        if (value.indexOf('&') >= 0) {
            value = MM.serialize(LEGACY.deserialize(value));
        }
        return MM.deserialize(value)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
