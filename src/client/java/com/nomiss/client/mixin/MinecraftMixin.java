package com.nomiss.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops you from swinging a sword, axe or mace at nothing.
 *
 * The game only puts an ENTITY in {@code hitResult} when you are aiming at an entity's
 * hitbox AND it is inside your entity interaction range. If you aim at empty air, or at
 * something that is out of reach, the result is MISS (or BLOCK if a block is behind it),
 * so a left click in that situation is cancelled here before the swing happens.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

	/**
	 * true  = you can still left-click blocks with a sword/axe/mace (chop wood, break cobwebs, etc.).
	 * false = strict mode: the weapon only swings when an entity is in reach, never at blocks.
	 */
	private static final boolean ALLOW_BLOCK_BREAKING = true;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void nomiss$cancelSwingAtNothing(CallbackInfoReturnable<Boolean> cir) {
if (java.nio.file.Files.exists(java.nio.file.Path.of("config", "nomiss.off"))) {
    return;
}
		Minecraft minecraft = (Minecraft) (Object) this;
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}

		ItemStack held = player.getMainHandItem();
		boolean isMeleeWeapon = held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES) || held.is(Items.MACE);
		if (!isMeleeWeapon) {
			return; // any other item behaves exactly like vanilla
		}

		HitResult target = minecraft.hitResult;
		if (target == null) {
			return; // let vanilla deal with its own error case
		}

		HitResult.Type type = target.getType();
		if (type == HitResult.Type.ENTITY) {
			return; // an entity is in reach and under the crosshair: allow the hit
		}
		if (type == HitResult.Type.BLOCK && ALLOW_BLOCK_BREAKING) {
			return;
		}

		cir.setReturnValue(false); // nothing to hit: cancel the swing
	}
}
