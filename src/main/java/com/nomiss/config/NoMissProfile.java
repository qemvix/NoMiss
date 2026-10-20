package com.nomiss.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NoMissProfile {
    private final String id;
    private String name;
    private boolean cooldown;
    private boolean blocks = true;
    private final List<WeaponSelection> slots = new ArrayList<>(Collections.nCopies(NoMissConfig.MAX_ITEMS, WeaponSelection.empty()));
    public NoMissProfile(String id, String name) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid profile ID");
        this.id = id; rename(name);
    }
    public String id() { return id; }
    public String name() { return name; }
    public void rename(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 48 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Use a profile name of 1–48 characters");
        name = value.strip();
    }
    public boolean cooldown() { return cooldown; }
    public void setCooldown(boolean value) { cooldown = value; }
    public boolean blocks() { return blocks; }
    public void setBlocks(boolean value) { blocks = value; }
    public WeaponSelection slot(int index) { return slots.get(index); }
    public List<WeaponSelection> slots() { return List.copyOf(slots); }
    public boolean setSlot(int index, WeaponSelection selection) {
        if (index < 0 || index >= slots.size() || selection == null) return false;
        if (!selection.isEmpty()) for (int i = 0; i < slots.size(); i++)
            if (i != index && selection.key().equals(slots.get(i).key())) return false;
        slots.set(index, selection); return true;
    }
    public void clear() { Collections.fill(slots, WeaponSelection.empty()); }
    public void defaultWeapons() {
        clear(); setSlot(0, WeaponSelection.category("swords")); setSlot(1, WeaponSelection.category("spears")); setSlot(2, WeaponSelection.category("maces"));
    }
    public NoMissProfile copy(String newId, String newName) {
        var result = new NoMissProfile(newId, newName);
        result.cooldown = cooldown; result.blocks = blocks;
        for (int i = 0; i < slots.size(); i++) result.slots.set(i, slots.get(i));
        return result;
    }
}
