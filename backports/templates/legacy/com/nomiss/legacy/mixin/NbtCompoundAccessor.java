package com.nomiss.legacy.mixin;
import net.minecraft.nbt.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;
@Mixin(NbtCompound.class)
public interface NbtCompoundAccessor {
    @Accessor("data") Map<String, NbtElement> nomiss$data();
}
