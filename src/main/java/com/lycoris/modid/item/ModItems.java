package com.lycoris.modid.item;

import com.lycoris.modid.LycorisAwakening;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item ARCANUM_ALLOY = registerItem("arcanum_alloy", new Item(new Item.Settings()));
    public static final Item LYCORITE = registerItem("lycorite", new Item(new Item.Settings()));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(LycorisAwakening.MOD_ID, name), item);
    }

    public static void registerModItems() {
        LycorisAwakening.LOGGER.info("Registering Mod Items for " + LycorisAwakening.MOD_ID);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(ARCANUM_ALLOY);
            entries.add(LYCORITE);
        });
    }
}
