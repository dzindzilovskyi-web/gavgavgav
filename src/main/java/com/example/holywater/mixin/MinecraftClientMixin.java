package com.example.holywater.mixin;

import com.example.holywater.HolyLogic;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void holywater$tick(CallbackInfo ci) {
        HolyLogic.tick((MinecraftClient) (Object) this);
    }
}
