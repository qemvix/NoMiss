package com.nomiss.config;

/** The attack rules shared by the input hook and regression tests. */
public final class AttackProtection {
    private AttackProtection() {}
    public enum Target { MISS, BLOCK, ENTITY }
    public enum BlockReason { OUT_OF_REACH, COOLDOWN, BLOCKS_DISABLED }

    public static BlockReason blockedReason(NoMissConfig config, String itemId, Target target, float attackStrength) {
        if (!config.enabled() || !config.contains(itemId)) return null;
        if (target == Target.BLOCK) return config.allowBlockBreaking() ? null : BlockReason.BLOCKS_DISABLED;
        if (target != Target.ENTITY) return BlockReason.OUT_OF_REACH;
        return config.blockDuringCooldown() && attackStrength < 1.0F ? BlockReason.COOLDOWN : null;
    }

    public static boolean shouldCancel(NoMissConfig config, String itemId, Target target, float attackStrength) {
        return blockedReason(config, itemId, target, attackStrength) != null;
    }
}
