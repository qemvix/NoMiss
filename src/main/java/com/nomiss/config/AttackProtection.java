package com.nomiss.config;

/** The attack rules shared by the input hook and regression tests. */
public final class AttackProtection {
    private AttackProtection() {}
    public enum Target { MISS, BLOCK, ENTITY }

    public static boolean shouldCancel(NoMissConfig config, String itemId, Target target, float attackStrength) {
        if (!config.enabled() || !config.contains(itemId)) return false;
        if (target == Target.BLOCK && config.allowBlockBreaking()) return false;
        if (target != Target.ENTITY) return true;
        return config.blockDuringCooldown() && attackStrength < 1.0F;
    }
}
