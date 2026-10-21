package com.nomiss.legacy;
import com.nomiss.config.AttackProtection.Target;
public final class LegacyTarget {
    private LegacyTarget() {}
    public static Target fromName(String name) {
        return switch (name) { case "TILE", "BLOCK" -> Target.BLOCK; case "ENTITY" -> Target.ENTITY; default -> Target.MISS; };
    }
}
