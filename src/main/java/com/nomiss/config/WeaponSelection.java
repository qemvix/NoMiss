package com.nomiss.config;

import java.util.Set;
import java.util.regex.Pattern;

/** A single slot, independent of the game registries. Exact matches store a digest, never item NBT. */
public record WeaponSelection(Kind kind, String value, String fingerprint, String label,
                              Override cooldown, Override blocks) {
    public enum Kind { EMPTY, ITEM, CATEGORY, EXACT }
    public enum Override {
        INHERIT, ON, OFF;
        public boolean resolve(boolean fallback) { return this == INHERIT ? fallback : this == ON; }
        public Override next() { return values()[(ordinal() + 1) % values().length]; }
    }
    public static final Set<String> CATEGORIES = Set.of("swords", "spears", "axes", "maces", "pickaxes", "shovels", "hoes");
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    public WeaponSelection {
        if (kind == null || value == null || fingerprint == null || label == null || cooldown == null || blocks == null)
            throw new IllegalArgumentException("Incomplete weapon selection");
        if (label.length() > 128 || label.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid item label");
        if (kind == Kind.CATEGORY && !CATEGORIES.contains(value)) throw new IllegalArgumentException("Unknown weapon category");
        if ((kind == Kind.ITEM || kind == Kind.EXACT) && (!ID.matcher(value).matches() || value.equals("minecraft:air")))
            throw new IllegalArgumentException("Invalid item ID");
        if (kind == Kind.EXACT && !fingerprint.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid exact-item signature");
        if (kind != Kind.EXACT && !fingerprint.isEmpty()) throw new IllegalArgumentException("Unexpected exact-item signature");
        if (kind == Kind.EMPTY && (!value.isEmpty() || !label.isEmpty())) throw new IllegalArgumentException("Invalid empty slot");
    }
    public static WeaponSelection empty() { return new WeaponSelection(Kind.EMPTY, "", "", "", Override.INHERIT, Override.INHERIT); }
    public static WeaponSelection item(String id) { return id.isEmpty() ? empty() : new WeaponSelection(Kind.ITEM, id, "", "", Override.INHERIT, Override.INHERIT); }
    public static WeaponSelection category(String id) { return new WeaponSelection(Kind.CATEGORY, id, "", "", Override.INHERIT, Override.INHERIT); }
    public static WeaponSelection exact(String id, String digest, String name) { return new WeaponSelection(Kind.EXACT, id, digest, name, Override.INHERIT, Override.INHERIT); }
    public WeaponSelection withRules(Override newCooldown, Override newBlocks) { return new WeaponSelection(kind, value, fingerprint, label, newCooldown, newBlocks); }
    public boolean isEmpty() { return kind == Kind.EMPTY; }
    public String key() { return kind == Kind.EMPTY ? "" : kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + value + (kind == Kind.EXACT ? ":" + fingerprint : ""); }
    public boolean matches(String item, Set<String> categories, String signature) {
        return switch (kind) {
            case EMPTY -> false;
            case ITEM -> value.equals(item);
            case CATEGORY -> categories.contains(value);
            case EXACT -> value.equals(item) && fingerprint.equals(signature);
        };
    }
    public int priority() { return switch (kind) { case EMPTY -> 0; case CATEGORY -> 1; case ITEM -> 2; case EXACT -> 3; }; }
}
