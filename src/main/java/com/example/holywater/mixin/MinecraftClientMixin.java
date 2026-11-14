package com.example.holywater.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    // Y: зелье, в названии которого есть «святая вода»
    @Unique private static final String HOLY_PART = "святая вода";
    // H: зелье, в названии которого есть «радиац»
    @Unique private static final String RADIATION_PART = "радиац";

    @Unique private boolean holywater$yWasDown = false;
    @Unique private boolean holywater$hWasDown = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void holywater$tick(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;

        long window = GLFW.glfwGetCurrentContext();
        boolean yDown = window != 0L && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Y) == GLFW.GLFW_PRESS;
        boolean hDown = window != 0L && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS;
        boolean yPressed = yDown && !holywater$yWasDown;
        boolean hPressed = hDown && !holywater$hWasDown;
        holywater$yWasDown = yDown;
        holywater$hWasDown = hDown;
        if (!yPressed && !hPressed) return;

        // не реагируем, если открыто меню или чат
        if (mc.currentScreen != null || mc.player == null || mc.interactionManager == null) return;
        if (mc.player.isSpectator()) return;

        if (yPressed) holywater$use(mc, HOLY_PART, "Святая вода");
        if (hPressed) holywater$use(mc, RADIATION_PART, "Зелье радиации");
    }

    // можно бросить: взрывное или задерживающееся зелье
    @Unique
    private static boolean holywater$isThrowable(ItemStack s) {
        return !s.isEmpty() && (s.isOf(Items.SPLASH_POTION) || s.isOf(Items.LINGERING_POTION));
    }

    // название так, как его видит игрок, без цветовых кодов, в нижнем регистре
    @Unique
    private static String holywater$nameOf(ItemStack s) {
        String n = s.getName().getString();
        return n.replaceAll("§.", "").replace('ё', 'е').toLowerCase(Locale.ROOT).trim();
    }

    @Unique
    private static boolean holywater$matches(ItemStack s, String part) {
        return holywater$isThrowable(s) && holywater$nameOf(s).contains(part);
    }

    @Unique
    private static void holywater$use(MinecraftClient mc, String part, String label) {
        ClientPlayerEntity player = mc.player;
        PlayerInventory inv = player.getInventory();

        // 1) во второй руке
        if (holywater$matches(player.getOffHandStack(), part)) {
            mc.interactionManager.interactItem(player, Hand.OFF_HAND);
            mc.inGameHud.setOverlayMessage(Text.literal(label + ": брошено"), false);
            return;
        }

        // 2) в хотбаре и основном инвентаре (индексы 0..35)
        int found = -1;
        for (int i = 0; i < 36; i++) {
            if (holywater$matches(inv.getStack(i), part)) { found = i; break; }
        }

        if (found < 0) {
            // подсказка: какие бросаемые зелья вообще есть в инвентаре
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getStack(i);
                if (holywater$isThrowable(s)) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append('«').append(s.getName().getString()).append('»');
                }
            }
            String have = sb.length() == 0 ? "нет взрывных зелий" : sb.toString();
            mc.inGameHud.getChatHud().addMessage(
                Text.literal("[Мод] " + label + " не найдено. В инвентаре: " + have));
            return;
        }

        int selected = inv.getSelectedSlot();
        // номер слота в окне инвентаря игрока: хотбар 36..44, основной инвентарь 9..35
        int slotId = found < 9 ? 36 + found : found;
        int syncId = player.playerScreenHandler.syncId;
        boolean swapped = found != selected;

        // временно ставим зелье в выбранный слот хотбара, бросаем, возвращаем обратно
        if (swapped) {
            mc.interactionManager.clickSlot(syncId, slotId, selected, SlotActionType.SWAP, player);
        }
        mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        if (swapped) {
            mc.interactionManager.clickSlot(syncId, slotId, selected, SlotActionType.SWAP, player);
        }
        mc.inGameHud.setOverlayMessage(Text.literal(label + ": брошено"), false);
    }
}
