package com.nomiss.legacy.mixin;
import com.nomiss.legacy.LegacyClient;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameOptions.class)
public abstract class OptionsMixin {
    @Inject(method = "<init>(Lnet/minecraft/client/MinecraftClient;Ljava/io/File;)V", at = @At("TAIL"))
    private void nomiss$keys(CallbackInfo ci) { LegacyClient.registerKeys((GameOptions)(Object)this); }
}
