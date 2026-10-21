package com.nomiss.legacy.mixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MinecraftClient.class)
public interface MinecraftAccessor {
    @Accessor("currentServerEntry") ServerInfo nomiss$server();
}
