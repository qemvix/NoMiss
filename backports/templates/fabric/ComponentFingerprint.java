package com.nomiss.client;

import com.google.gson.*;
import net.minecraft.nbt.*;
import java.util.TreeSet;

/** Typed NBT canonicalization: sort compound keys, retain list order and numeric types. */
final class ComponentFingerprint {
    static JsonElement nbt(Tag tag) {
        var result = new JsonObject();
        result.addProperty("type", tag.getId());
        if (tag instanceof CompoundTag compound) {
            var value = new JsonObject();
            for (String key : new TreeSet<>(compound.getAllKeys())) value.add(key, nbt(compound.get(key)));
            result.add("value", value);
        } else if (tag instanceof ListTag list) {
            var value = new JsonArray();
            for (Tag entry : list) value.add(nbt(entry));
            result.add("value", value);
        } else result.addProperty("value", tag.toString());
        return result;
    }
}
