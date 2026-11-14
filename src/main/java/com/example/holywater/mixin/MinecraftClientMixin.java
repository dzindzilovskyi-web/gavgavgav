package com.example.holywater.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
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

    // Y: зелье исцеления с названием «святая вода»
    @Unique private static final String HOLY_NAME = "святая вода";
    // H: любое взрывное зелье, в названии которого есть «радиац»
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

        if (yPressed) holywater$use(mc, false);
        if (hPressed) holywater$use(mc, true);
    }

    @Unique
    private static boolean holywater$isHolyWater(ItemStack stack) {
        if (stack.isEmpty() || !stack.isOf(Items.SPLASH_POTION)) return false;

        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (name == null || !name.getString().trim().equalsIgnoreCase(HOLY_NAME)) return false;

        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return false;
        return contents.potion()
            .map(p -> p.equals(Potions.HEALING) || p.equals(Potions.STRONG_HEALING))
            .orElse(false);
    }

    @Unique
    private static boolean holywater$isRadiation(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!stack.isOf(Items.SPLASH_POTION) && !stack.isOf(Items.LINGERING_POTION)) return false;

        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (name == null) return false;
        return name.getString().toLowerCase(Locale.ROOT).contains(RADIATION_PART);
    }

    @Unique
    private static void holywater$use(MinecraftClient mc, boolean radiation) {
        ClientPlayerEntity player = mc.player;
        PlayerInventory inv = player.getInventory();

        // ищем в хотбаре и основном инвентаре (индексы 0..35)
        int found = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            boolean ok = radiation ? holywater$isRadiation(s) : holywater$isHolyWater(s);
            if (ok) { found = i; break; }
        }
        if (found < 0) {
            mc.inGameHud.setOverlayMessage(
                Text.literal(radiation ? "Зелье радиации не найдено" : "Святая вода не найдена"), false);
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
    }
}
