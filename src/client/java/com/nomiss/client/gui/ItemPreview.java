package com.nomiss.client.gui;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class ItemPreview {
    private ItemPreview() {}

    static ItemStack of(Item item) {
        if (item.builtInRegistryHolder().areComponentsBound()) return new ItemStack(item);
        // Since 26.3, item prototypes are bound when joining a world. A detached preview
        // supplies only the visual components before then, without mutating any registry.
        var visualComponents = DataComponentMap.builder()
            .set(DataComponents.ITEM_NAME, Component.translatable(item.getDescriptionId()))
            .set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(item))
            .build();
        return new ItemStack(Holder.direct(item, visualComponents));
    }
}
