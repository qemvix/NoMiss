package com.nomiss.legacy.mixin;
import net.minecraft.nbt.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;
@Mixin(NbtList.class)
public interface NbtListAccessor {
    @Accessor("value") List<NbtElement> nomiss$value();
}
