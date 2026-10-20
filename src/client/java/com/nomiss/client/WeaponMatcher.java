package com.nomiss.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import com.nomiss.config.NoMissConfig;
import com.nomiss.config.WeaponSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class WeaponMatcher {
    private WeaponMatcher() {}
    public static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static boolean tagged(ItemStack stack, TagKey<Item> vanilla, String common) {
        return stack.is(holder -> holder.is(vanilla) || holder.is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/" + common))));
    }
    public static Set<String> categories(ItemStack stack) {
        if (stack.isEmpty()) return Set.of();
        var result = new HashSet<String>();
        if (tagged(stack, ItemTags.SWORDS, "sword")) result.add("swords");
        if (tagged(stack, ItemTags.SPEARS, "spear") || stack.has(DataComponents.PIERCING_WEAPON)) result.add("spears");
        if (tagged(stack, ItemTags.AXES, "axe")) result.add("axes");
        if (tagged(stack, ItemTags.PICKAXES, "pickaxe")) result.add("pickaxes");
        if (tagged(stack, ItemTags.SHOVELS, "shovel")) result.add("shovels");
        if (tagged(stack, ItemTags.HOES, "hoe")) result.add("hoes");
        if (id(stack).equals("minecraft:mace") || stack.is(holder -> holder.is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/mace"))))) result.add("maces");
        return Set.copyOf(result);
    }
    public static NoMissConfig.Rules resolve(ItemStack stack) {
        if (stack.isEmpty()) return null;
        var config = NoMissClient.config();
        String id = id(stack);
        boolean needsSignature = config.activeProfile().slots().stream().anyMatch(s -> s.kind() == WeaponSelection.Kind.EXACT && s.value().equals(id));
        return config.resolve(id, categories(stack), needsSignature ? fingerprint(stack).orElse("") : "");
    }
    public static Optional<String> fingerprint(ItemStack stack) {
        var client = Minecraft.getInstance();
        if (stack.isEmpty() || client.level == null) return Optional.empty();
        try {
            var components = stack.getComponents().filter(type -> type != DataComponents.DAMAGE && type != DataComponents.REPAIR_COST);
            var json = DataComponentMap.CODEC.encodeStart(client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), components).result();
            if (json.isEmpty()) return Optional.empty();
            var digest = MessageDigest.getInstance("SHA-256").digest(canonical(json.get()).toString().getBytes(StandardCharsets.UTF_8));
            return Optional.of(HexFormat.of().formatHex(digest));
        } catch (RuntimeException | java.security.NoSuchAlgorithmException e) { return Optional.empty(); }
    }
    public static Optional<WeaponSelection> exactSelection(ItemStack stack) {
        String name = stack.getHoverName().getString().replaceAll("[\\p{Cntrl}]", " ");
        String label = name.substring(0, Math.min(128, name.length()));
        return fingerprint(stack).map(digest -> WeaponSelection.exact(id(stack), digest, label));
    }
    private static JsonElement canonical(JsonElement element) {
        if (element.isJsonObject()) {
            var result = new JsonObject();
            new TreeMap<>(element.getAsJsonObject().asMap()).forEach((key, value) -> result.add(key, canonical(value)));
            return result;
        }
        if (element.isJsonArray()) { var result = new JsonArray(); for (var value : element.getAsJsonArray()) result.add(canonical(value)); return result; }
        return element;
    }
}
