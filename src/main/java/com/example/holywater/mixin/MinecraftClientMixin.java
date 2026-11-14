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

    // многотиковая последовательность: 0 = ничего, 1 = пора бросать, 2 = пора вернуть
    @Unique private int holywater$stage = 0;
    @Unique private int holywater$delay = 0;
    @Unique private int holywater$mode = 0;            // 0 = обмен слотами, 1 = смена слота хотбара
    @Unique private int holywater$slotId = 0;          // слот в окне инвентаря (для обмена)
    @Unique private int holywater$selected = 0;        // выбранный слот хотбара до начала

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
            holywater$stage = 0;
            return;
        }
        if (!holywater$greeted) {
            holywater$greeted = true;
            holywater$msg(mc, "[Мод] загружен. Y = святая вода, H = зелье радиации");
        }
        if (mc.interactionManager == null) return;

        // идёт последовательность броска: ведём её дальше, новые нажатия игнорируем
        if (holywater$stage != 0) {
            holywater$advance(mc);
            return;
        }

        if (!yPressed && !hPressed) return;
        if (mc.currentScreen != null) return;
        if (mc.player.isSpectator()) return;

        if (yPressed) holywater$use(mc, HOLY_PART, "Святая вода");
        else if (hPressed) holywater$use(mc, RADIATION_PART, "Зелье радиации");
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

    // шаг 1: находим зелье и готовим его (кладём в руку)
    @Unique
    private void holywater$use(MinecraftClient mc, String part, String label) {
        ClientPlayerEntity player = mc.player;
        PlayerInventory inv = player.getInventory();

        // зелье во второй руке: бросаем сразу
        ItemStack off = player.getOffHandStack();
        if (holywater$matches(off, part)) {
            ActionResult r = mc.interactionManager.interactItem(player, Hand.OFF_HAND);
            holywater$msg(mc, "[Мод] " + label + ": из второй руки, результат: " + r);
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
                    sb.append(holywater$idOf(s)).append(" «").append(s.getName().getString()).append('»');
                }
            }
            holywater$msg(mc, "[Мод] " + label + " не найдено. Зелья в инвентаре: "
                + (sb.length() == 0 ? "нет" : sb.toString()));
            return;
        }

        holywater$msg(mc, "[Мод] " + label + ": найдено в слоте " + found);

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
        holywater$delay = 4; // 4 тика (0.2 с) паузы перед броском
    }

    // шаги 2 и 3: бросаем, потом возвращаем всё на место
    @Unique
    private void holywater$advance(MinecraftClient mc) {
        if (holywater$delay > 0) {
            holywater$delay--;
            return;
        }
        ClientPlayerEntity player = mc.player;
        PlayerInventory inv = player.getInventory();

        if (holywater$stage == 1) {
            ActionResult r = mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
            holywater$msg(mc, "[Мод] бросок, результат: " + r);
            holywater$stage = 2;
            holywater$delay = 4;
        } else {
            if (holywater$mode == 0) {
                int syncId = player.playerScreenHandler.syncId;
                mc.interactionManager.clickSlot(syncId, holywater$slotId, holywater$selected,
                    SlotActionType.SWAP, player);
            } else {
                inv.setSelectedSlot(holywater$selected);
            }
            holywater$stage = 0;
        }
    }
}
