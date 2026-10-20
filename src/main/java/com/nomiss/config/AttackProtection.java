package com.nomiss.config;

/** The attack rules shared by the input hook and regression tests. */
public final class AttackProtection {
    private AttackProtection() {}
    public enum Target { MISS, BLOCK, ENTITY }
    public enum BlockReason { OUT_OF_REACH, COOLDOWN, BLOCKS_DISABLED }

    public static BlockReason blockedReason(NoMissConfig config, String itemId, Target target, float attackStrength) {
        return blockedReason(config.enabled(), config.resolve(itemId, NoMissConfig.vanillaCategories(itemId), ""), target, attackStrength);
    }

    public static BlockReason blockedReason(boolean enabled, NoMissConfig.Rules rules, Target target, float attackStrength) {
        if (!enabled || rules == null) return null;
        if (target == Target.BLOCK) return rules.blocks() ? null : BlockReason.BLOCKS_DISABLED;
        if (target != Target.ENTITY) return BlockReason.OUT_OF_REACH;
        return rules.cooldown() && attackStrength < 1.0F ? BlockReason.COOLDOWN : null;
    }

    public static boolean shouldCancel(NoMissConfig config, String itemId, Target target, float attackStrength) {
        return blockedReason(config, itemId, target, attackStrength) != null;
    }
}
