package com.nomiss.legacy.mixin;
import com.nomiss.legacy.LegacyClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftClient.class)
public abstract class ClientMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void nomiss$tick(CallbackInfo ci) { LegacyClient.tick(); }
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void nomiss$attack(CallbackInfo ci) { if (LegacyClient.blockAttack()) ci.cancel(); }
    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void nomiss$mine(boolean active, CallbackInfo ci) { if (active && LegacyClient.blockMining()) ci.cancel(); }
}
