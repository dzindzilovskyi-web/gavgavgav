package com.example.holywater.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
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

    // Y: предмет, в названии которого есть «святая вода»
    @Unique private static final String HOLY_PART = "святая вода";
    // H: предмет, в названии которого есть «радиац»
    @Unique private static final String RADIATION_PART = "радиац";

    @Unique private boolean holywater$yWasDown = false;
    @Unique private boolean holywater$hWasDown = false;
    @Unique private boolean holywater$greeted = false;

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

        if (mc.player == null) {
            holywater$greeted = false;
            return;
        }
        if (!holywater$greeted) {
            holywater$greeted = true;
            holywater$msg(mc, "[Мод] загружен. Y = святая вода, H = зелье радиации");
        }

        if (!yPressed && !hPressed) return;
        if (mc.currentScreen != null || mc.interactionManager == null) return;
        if (mc.player.isSpectator()) return;

        if (yPressed) {
            holywater$msg(mc, "[Мод] нажата Y");
            holywater$use(mc, HOLY_PART, "Святая вода");
        }
        if (hPressed) {
            holywater$msg(mc, "[Мод] нажата H");
            holywater$use(mc, RADIATION_PART, "Зелье радиации");
        }
    }

    @Unique
    private static void holywater$msg(MinecraftClient mc, String text) {
        mc.inGameHud.getChatHud().addMessage(Text.literal(text));
    }

    // название так, как его видит игрок, в нижнем регистре
    @Unique
    private static String holywater$nameOf(ItemStack s) {
        String n = s.getName().getString();
        return n.replaceAll("§.", "").replace('ё', 'е').toLowerCase(Locale.ROOT).trim();
    }

    // ищем только по названию, тип предмета не проверяем
    @Unique
    private static boolean holywater$matches(ItemStack s, String part) {
        return !s.isEmpty() && holywater$nameOf(s).contains(part);
    }

    @Unique
    private static String holywater$idOf(ItemStack s) {
        return Registries.ITEM.getId(s.getItem()).toString();
    }

    @Unique
    private static void holywater$use(MinecraftClient mc, String part, String label) {
        ClientPlayerEntity player = mc.player;
        PlayerInventory inv = player.getInventory();

        // 1) во второй руке
        ItemStack off = player.getOffHandStack();
        if (holywater$matches(off, part)) {
            holywater$msg(mc, "[Мод] " + label + ": найдено во второй руке (" + holywater$idOf(off) + ")");
            ActionResult r = mc.interactionManager.interactItem(player, Hand.OFF_HAND);
            holywater$msg(mc, "[Мод] результат: " + r.getClass().getSimpleName());
            return;
        }

        // 2) в хотбаре и основном инвентаре (индексы 0..35)
        int found = -1;
        for (int i = 0; i < 36; i++) {
            if (holywater$matches(inv.getStack(i), part)) { found = i; break; }
        }

        if (found < 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && holywater$idOf(s).contains("potion")) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(holywater$idOf(s)).append(" «").append(s.getName().getString()).append('»');
                }
            }
            holywater$msg(mc, "[Мод] " + label + " не найдено. Зелья в инвентаре: "
                + (sb.length() == 0 ? "нет" : sb.toString()));
            return;
        }

        ItemStack target = inv.getStack(found);
        holywater$msg(mc, "[Мод] " + label + ": найдено в слоте " + found + " (" + holywater$idOf(target) + ")");

        int selected = inv.getSelectedSlot();
        // номер слота в окне инвентаря игрока: хотбар 36..44, основной инвентарь 9..35
        int slotId = found < 9 ? 36 + found : found;
        int syncId = player.playerScreenHandler.syncId;
        boolean swapped = found != selected;

        // временно ставим предмет в выбранный слот хотбара, используем, возвращаем обратно
        if (swapped) {
            mc.interactionManager.clickSlot(syncId, slotId, selected, SlotActionType.SWAP, player);
        }
        ActionResult r = mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
        if (swapped) {
            mc.interactionManager.clickSlot(syncId, slotId, selected, SlotActionType.SWAP, player);
        }
        holywater$msg(mc, "[Мод] результат: " + r.getClass().getSimpleName());
    }
}
