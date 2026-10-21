package com.nomiss.legacy;

import com.google.gson.*;
import com.nomiss.config.*;
import com.nomiss.legacy.mixin.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class LegacyMatcher {
    private LegacyMatcher() {}
    public static String id(ItemStack stack) { return Item.REGISTRY.getIdentifier(stack.getItem()).toString(); }
    public static Set<String> categories(ItemStack stack) {
        var result = new HashSet<String>(); var item = stack.getItem();
        if (item instanceof SwordItem) result.add("swords");
        if (item instanceof AxeItem) result.add("axes");
        if (item instanceof PickaxeItem) result.add("pickaxes");
        if (item instanceof ShovelItem) result.add("shovels");
        if (item instanceof HoeItem) result.add("hoes");
        return result;
    }
    public static NoMissConfig.Rules resolve(ItemStack stack) {
        if (stack == null) return null;
        boolean exact = LegacyClient.config().activeProfile().slots().stream()
            .anyMatch(s -> s.kind() == WeaponSelection.Kind.EXACT && s.value().equals(id(stack)));
        return LegacyClient.config().resolve(id(stack), categories(stack), exact ? fingerprint(stack).orElse("") : "");
    }
    public static Optional<WeaponSelection> exact(ItemStack stack) {
        String name = stack.getCustomName().replaceAll("[\\p{Cntrl}]", " ");
        return fingerprint(stack).map(value -> WeaponSelection.exact(id(stack), value, name.substring(0, Math.min(128, name.length()))));
    }
    public static Optional<String> fingerprint(ItemStack stack) {
        try {
            var object = new JsonObject();
            if (stack.getNbt() != null) {
                var compound = new JsonObject();
                var values = ((NbtCompoundAccessor) stack.getNbt()).nomiss$data();
                for (String key : new TreeSet<>(values.keySet())) if (!key.equals("Damage") && !key.equals("RepairCost")) compound.add(key, nbt(values.get(key)));
                if (compound.size() > 0) object.add("nbt", compound);
            }
            // Old metadata selects item variants. Damageable items use it only for durability.
            if (stack.getMaxDamage() <= 0) object.addProperty("metadata", stack.getDamage());
            return Optional.of(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(object.toString().getBytes(StandardCharsets.UTF_8))));
        } catch (RuntimeException | java.security.NoSuchAlgorithmException e) { return Optional.empty(); }
    }
    private static JsonElement nbt(NbtElement element) {
        var result = new JsonObject(); result.addProperty("type", element.getType());
        if (element instanceof NbtCompound compound) {
            var values = ((NbtCompoundAccessor) compound).nomiss$data(); var object = new JsonObject();
            for (String key : new TreeSet<>(values.keySet())) object.add(key, nbt(values.get(key)));
            result.add("value", object);
        } else if (element instanceof NbtList list) {
            var array = new JsonArray(); for (var entry : ((NbtListAccessor) list).nomiss$value()) array.add(nbt(entry));
            result.add("value", array);
        } else result.addProperty("value", element.toString());
        return result;
    }
}
