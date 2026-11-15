package com.example.holywater;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public final class HolyUtil {
    private HolyUtil() {}

    // Скорость: пауза в тиках (1 тик = 0.05 с). Если не срабатывает, поставьте 2.
    public static final int DELAY_BEFORE = 1;
    public static final int DELAY_AFTER = 1;

    // клавиши: Q, H, B, N
    public static final int[] KEYS = {
        GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N
    };
    // что искать в названии предмета (без ё, в нижнем регистре)
    public static final String[] PARTS = {
        "святая вода", "радиац", "паллад", "ассасин"
    };
    // как называть в сообщениях
    public static final String[] LABELS = {
        "Святая вода", "Зелье радиации", "Зелье Палладина", "Зелье Ассасина"
    };

    public static void msg(MinecraftClient mc, String text) {
        mc.inGameHud.getChatHud().addMessage(Text.literal(text));
    }

    // название так, как его видит игрок, в нижнем регистре
    public static String nameOf(ItemStack s) {
        String n = s.getName().getString();
        return n.replaceAll("§.", "").replace('ё', 'е').toLowerCase(Locale.ROOT).trim();
    }

    // ищем только по названию, тип предмета не проверяем
    public static boolean matches(ItemStack s, String part) {
        return !s.isEmpty() && nameOf(s).contains(part);
    }

    public static String idOf(ItemStack s) {
        return Registries.ITEM.getId(s.getItem()).toString();
    }
}
