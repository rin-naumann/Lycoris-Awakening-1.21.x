package com.lycoris.modid.item;

import com.lycoris.modid.LycorisAwakening;
import com.lycoris.modid.item.custom.LifeInfuserItem;
import com.lycoris.modid.item.custom.SanguineSwordItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    // Basic Items
    public static final Item ARCANUM_ALLOY = registerItem("arcanum_alloy", new Item(new Item.Settings()));
    public static final Item LYCORITE = registerItem("lycorite", new Item(new Item.Settings()));
    public static final Item LYCORITE_PETAL = registerItem("lycorite_petal", new Item(new Item.Settings()));
    public static final Item BLOOD_DIAMOND = registerItem("blood_diamond", new Item(new Item.Settings()));
    public static final Item LIFE_INFUSER = registerItem("life_infuser", new LifeInfuserItem(new Item.Settings()));

    // Lycorite Items
    public static final Item LYCORITE_SWORD = registerItem("lycorite_sword", new Item(new Item.Settings()));


    // Special Items
    public static final Item SANGUINE_SOLILOQUY = registerItem("sanguine_soliloquy", new SanguineSwordItem(ModToolMaterials.LYCORITE, new Item.Settings()
            .attributeModifiers(SwordItem.createAttributeModifiers(ModToolMaterials.LYCORITE, 3, 3f))
    ));

    // New Enchantments

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(LycorisAwakening.MOD_ID, name), item);
    }

    public static void registerModItems() {
        LycorisAwakening.LOGGER.info("Registering Mod Items for " + LycorisAwakening.MOD_ID);

    }
}
