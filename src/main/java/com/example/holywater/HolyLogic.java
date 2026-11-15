package com.example.holywater;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class HolyLogic {
    private HolyLogic() {}

    private static final boolean[] wasDown = new boolean[4];
    private static boolean greeted = false;

    public static void tick(MinecraftClient mc) {
        long window = GLFW.glfwGetCurrentContext();
        int pressedIndex = -1;
        for (int i = 0; i < HolyUtil.KEYS.length; i++) {
            boolean down = window != 0L
                && GLFW.glfwGetKey(window, HolyUtil.KEYS[i]) == GLFW.GLFW_PRESS;
            if (down && !wasDown[i] && pressedIndex < 0) pressedIndex = i;
            wasDown[i] = down;

            // Q по умолчанию выбрасывает предмет из руки: в игре (не в меню) гасим это нажатие
            if (down && HolyUtil.KEYS[i] == GLFW.GLFW_KEY_Q
                && mc.player != null && mc.currentScreen == null) {
                while (mc.options.dropKey.wasPressed()) {
                    // просто съедаем нажатия
                }
            }
        }

        if (mc.player == null) {
            greeted = false;
            HolyAction.reset();
            return;
        }
        if (!greeted) {
            greeted = true;
            HolyUtil.msg(mc, "[Мод] загружен. Q = святая вода, H = радиация, B = Палладин, N = Ассасин");
        }
        if (mc.interactionManager == null) return;

        // идёт действие: ведём его дальше, новые нажатия игнорируем
        if (HolyAction.busy()) {
            HolyAction.advance(mc);
            return;
        }

        if (pressedIndex < 0) return;
        if (mc.currentScreen != null) return;
        if (mc.player.isSpectator()) return;

        HolyAction.start(mc, HolyUtil.PARTS[pressedIndex], HolyUtil.LABELS[pressedIndex]);
    }
}
