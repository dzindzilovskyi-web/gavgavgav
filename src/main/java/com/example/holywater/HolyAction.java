package com.example.holywater;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

public final class HolyAction {
    private HolyAction() {}

    // 0 = ничего, 1 = пора использовать, 2 = пора вернуть, 3 = идёт питьё
    private static int stage = 0;
    private static int delay = 0;
    private static int timeout = 0;
    private static int mode = 0;      // 0 = обмен слотами, 1 = смена слота хотбара
    private static int slotId = 0;
    private static int selected = 0;

    public static boolean busy() {
        return stage != 0;
    }

    public static void reset() {
        stage = 0;
    }

    // шаг 1: находим предмет и готовим его (кладём в руку)
    public static void start(MinecraftClient mc, String part, String label) {
        ClientPlayerEntity player = mc.player;
        PlayerInventory inv = player.getInventory();

        // предмет во второй руке: используем сразу
        if (HolyUtil.matches(player.getOffHandStack(), part)) {
            mc.interactionManager.interactItem(player, Hand.OFF_HAND);
            mc.inGameHud.setOverlayMessage(Text.literal(label + ": использовано"), false);
            mode = 2;
            if (player.isUsingItem()) {
                stage = 3;
                timeout = 80;
                mc.options.useKey.setPressed(true);
            }
            return;
        }

        // ищем в хотбаре и основном инвентаре (индексы 0..35)
        int found = -1;
        for (int i = 0; i < 36; i++) {
            if (HolyUtil.matches(inv.getStack(i), part)) { found = i; break; }
        }

        if (found < 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && HolyUtil.idOf(s).contains("potion")) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append('«').append(s.getName().getString()).append('»');
                }
            }
            HolyUtil.msg(mc, "[Мод] " + label + " не найдено. Зелья в инвентаре: "
                + (sb.length() == 0 ? "нет" : sb.toString()));
            return;
        }

        mc.inGameHud.setOverlayMessage(Text.literal(label + ": использовано"), false);

        selected = inv.getSelectedSlot();

        if (found < 9) {
            // в хотбаре: просто переключаем слот
            mode = 1;
            inv.setSelectedSlot(found);
        } else {
            // в основном инвентаре: меняем местами с выбранным слотом хотбара
            mode = 0;
            slotId = found;
            int syncId = player.playerScreenHandler.syncId;
            mc.interactionManager.clickSlot(syncId, found, selected, SlotActionType.SWAP, player);
        }

        stage = 1;
        delay = HolyUtil.DELAY_BEFORE;
    }

    // шаги 2 и 3: используем, ждём окончания питья, возвращаем всё на место
    public static void advance(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        PlayerInventory inv = player.getInventory();

        // идёт питьё: удерживаем «использование», пока предмет не будет выпит
        if (stage == 3) {
            if (player.isUsingItem() && timeout-- > 0) {
                mc.options.useKey.setPressed(true);
                return;
            }
            mc.options.useKey.setPressed(false);
            stage = 2;
            delay = HolyUtil.DELAY_AFTER;
            return;
        }

        if (delay > 0) {
            delay--;
            return;
        }

        if (stage == 1) {
            mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
            if (player.isUsingItem()) {
                // предмет надо пить: держим кнопку
                stage = 3;
                timeout = 80;
                mc.options.useKey.setPressed(true);
            } else {
                stage = 2;
                delay = HolyUtil.DELAY_AFTER;
            }
        } else {
            // возвращаем всё как было
            if (mode == 0) {
                int syncId = player.playerScreenHandler.syncId;
                mc.interactionManager.clickSlot(syncId, slotId, selected, SlotActionType.SWAP, player);
            } else if (mode == 1) {
                inv.setSelectedSlot(selected);
            }
            stage = 0;
        }
    }
}
