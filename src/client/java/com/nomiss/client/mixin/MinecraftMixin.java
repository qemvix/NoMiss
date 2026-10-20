package com.nomiss.client.mixin;

import com.nomiss.client.NoMissClient;
import com.nomiss.client.NoMissHud;
import com.nomiss.client.WeaponMatcher;
import com.nomiss.config.AttackProtection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void nomiss$cancelWastedAttack(CallbackInfoReturnable<Boolean> cir) {
        var config = NoMissClient.config();
        Minecraft client = (Minecraft) (Object) this;
        if (!config.enabled() || client.player == null || client.hitResult == null || client.player.isSpectator()) return;
        var held = client.player.getMainHandItem();
        var rules = WeaponMatcher.resolve(held);
        if (rules == null) return;
        var target = AttackProtection.Target.valueOf(client.hitResult.getType().name());

        if (target != AttackProtection.Target.BLOCK || !rules.blocks()) {
            if (held.has(DataComponents.PIERCING_WEAPON)) {
                // Spears have their own minimum/maximum reach and piercing raycast. Match vanilla's
                // stab query so a too-close entity or an entity behind a wall doesn't waste a jab.
                boolean canHit = ProjectileUtil.getHitEntitiesAlong(client.player, client.player.getAttackRangeWith(held),
                    entity -> PiercingWeapon.canHitEntity(client.player, entity), ClipContext.Block.COLLIDER)
                    .map(block -> false, entities -> !entities.isEmpty());
                target = canHit ? AttackProtection.Target.ENTITY : AttackProtection.Target.MISS;
            } else if (client.hitResult instanceof EntityHitResult entityHit
                && !client.player.isWithinAttackRange(held, entityHit.getEntity().getBoundingBox(), 0.0)) {
                target = AttackProtection.Target.MISS;
            }
        }
        var reason = AttackProtection.blockedReason(config.enabled(), rules, target, client.player.getAttackStrengthScale(0.0F));
        if (reason != null) {
            NoMissHud.blocked(reason);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void nomiss$preventHeldBlockAttack(boolean attacking, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        var config = NoMissClient.config();
        if (!attacking || !config.enabled() || client.player == null
            || client.gameMode == null || client.hitResult == null || client.player.isSpectator()) return;
        var rules = WeaponMatcher.resolve(client.player.getMainHandItem());
        if (rules != null && !rules.blocks() && client.hitResult.getType() == HitResult.Type.BLOCK) {
            NoMissHud.blocked(AttackProtection.BlockReason.BLOCKS_DISABLED);
            client.gameMode.stopDestroyBlock();
            ci.cancel();
        }
    }
}
