package com.example.holywater.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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

    // Скорость: пауза в тиках (1 тик = 0.05 с).
    // DELAY_BEFORE: между «взял в руку» и «использовал».
    // DELAY_AFTER: между «использовал» и «вернул на место».
    // 0 = самое быстрое, но сервер может отменить действие. Если не срабатывает, поставьте 2.
    @Unique private static final int DELAY_BEFORE = 1;
    @Unique private static final int DELAY_AFTER = 1;

    // клавиши: Q, H, B, N
    @Unique private static final int[] KEYS = {
        GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N
    };
    // что искать в названии предмета (без ё, в нижнем регистре)
    @Unique private static final String[] PARTS = {
        "святая вода", "радиац", "паллад", "ассасин"
    };
    // как называть в сообщениях
    @Unique private static final String[] LABELS = {
        "Святая вода", "Зелье радиации", "Зелье Палладина", "Зелье Ассасина"
    };

    @Unique private boolean[] holywater$wasDown = new boolean[4];
    @Unique private boolean holywater$greeted = false;

    // последовательность: 0 = ничего, 1 = пора использовать, 2 = пора вернуть, 3 = идёт питьё
    @Unique private int holywater$stage = 0;
    @Unique private int holywater$delay = 0;
    @Unique private int holywater$timeout = 0;
    @Unique private int holywater$mode = 0;      // 0 = обмен слотами, 1 = смена слота хотбара, 2 = вторая рука
    @Unique private int holywater$slotId = 0;    // слот в окне инвентаря (для обмена)
    @Unique private int holywater$selected = 0;  // выбранный слот хотбара до начала

    @Inject(method = "tick", at = @At("HEAD"))
    private void holywater$tick(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;

        long window = GLFW.glfwGetCurrentContext();
        int pressedIndex = -1;
        for (int i = 0; i < KEYS.length; i++) {
            boolean down = window != 0L && GLFW.glfwGetKey(window, KEYS[i]) == GLFW.GLFW_PRESS;
            if (down && !holywater$wasDown[i] && pressedIndex < 0) pressedIndex = i;
            holywater$wasDown[i] = down;

            // Q по умолчанию выбрасывает предмет из руки: в игре (не в меню) гасим это нажатие
            if (down && KEYS[i] == GLFW.GLFW_KEY_Q && mc.player != null && mc.currentScreen == null) {
                while (mc.options.dropKey.wasPressed()) {
                    // просто съедаем нажатия
                }
            }
        }

        if (mc.player == null) {
            holywater$greeted = false;
            holywater$stage = 0;
            return;
        }
        if (!holywater$greeted) {
            holywater$greeted = true;
            holywater$msg(mc, "[Мод] загружен. Q = святая вода, H = радиация, B = Палладин, N = Ассасин");
        }
        if (mc.interactionManager == null) return;

        // идёт последовательность: ведём её дальше, новые нажатия игнорируем
        if (holywater$stage != 0) {
            holywater$advance(mc);
            return;
        }

        if (pressedIndex < 0) return;
        if (mc.currentScreen != null) return;
        if (mc.player.isSpectator()) return;

        holywater$use(mc, PARTS[pressedIndex], LABELS[pressedIndex]);
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

    // шаг 1: находим предмет и готовим его (кладём в руку)
    @Unique
    private void holywater$use(MinecraftClient mc, String part, String label) {
        ClientPlayerEntity player = mc.player;
        PlayerInventory inv = player.getInventory();

        // предмет во второй руке: используем сразу
        if (holywater$matches(player.getOffHandStack(), part)) {
            mc.interactionManager.interactItem(player, Hand.OFF_HAND);
            mc.inGameHud.setOverlayMessage(Text.literal(label + ": использовано"), false);
            holywater$mode = 2;
            if (player.isUsingItem()) {
                holywater$stage = 3;
                holywater$timeout = 80;
                mc.options.useKey.setPressed(true);
            }
            return;
        }

        // ищем в хотбаре и основном инвентаре (индексы 0..35)
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
                    sb.append('«').append(s.getName().getString()).append('»');
                }
            }
            holywater$msg(mc, "[Мод] " + label + " не найдено. Зелья в инвентаре: "
                + (sb.length() == 0 ? "нет" : sb.toString()));
            return;
        }

        mc.inGameHud.setOverlayMessage(Text.literal(label + ": использовано"), false);

        int selected = inv.getSelectedSlot();
        holywater$selected = selected;

        if (found < 9) {
            // в хотбаре: просто переключаем слот
            holywater$mode = 1;
            inv.setSelectedSlot(found);
        } else {
            // в основном инвентаре: меняем местами с выбранным слотом хотбара
            holywater$mode = 0;
            holywater$slotId = found; // слоты 9..35 совпадают с номерами в окне игрока
            int syncId = player.playerScreenHandler.syncId;
            mc.interactionManager.clickSlot(syncId, found, selected, SlotActionType.SWAP, player);
        }

        holywater$stage = 1;
        holywater$delay = DELAY_BEFORE;
    }

    // шаги 2 и 3: используем, ждём окончания питья, возвращаем всё на место
    @Unique
    private void holywater$advance(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        PlayerInventory inv = player.getInventory();

        // идёт питьё: удерживаем «использование», пока предмет не будет выпит
        if (holywater$stage == 3) {
            if (player.isUsingItem() && holywater$timeout-- > 0) {
                mc.options.useKey.setPressed(true);
                return;
            }
            mc.options.useKey.setPressed(false);
            holywater$stage = 2;
            holywater$delay = DELAY_AFTER;
            return;
        }

        if (holywater$delay > 0) {
            holywater$delay--;
            return;
        }
