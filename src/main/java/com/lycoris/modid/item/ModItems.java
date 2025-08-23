package com.lycoris.modid.item;

import com.lycoris.modid.LycorisAwakening;
import com.lycoris.modid.item.custom.LifeInfuserItem;
import com.lycoris.modid.item.custom.MementoMoriItem;
import com.lycoris.modid.item.custom.SanguineSwordItem;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import javax.tools.Tool;

public class ModItems {

    // Basic Items
    public static final Item ARCANUM_ALLOY = registerItem("arcanum_alloy", new Item(new Item.Settings()));
    public static final Item LYCORITE = registerItem("lycorite", new Item(new Item.Settings()));
    public static final Item LYCORITE_PETAL = registerItem("lycorite_petal", new Item(new Item.Settings()));
    public static final Item BLOOD_DIAMOND = registerItem("blood_diamond", new Item(new Item.Settings()));
    public static final Item LIFE_INFUSER = registerItem("life_infuser", new LifeInfuserItem(new Item.Settings()));

    // Lycorite Items
    public static final Item LYCORITE_SWORD = registerItem("lycorite_sword", new SwordItem(ModToolMaterials.LYCORITE, new Item.Settings()
            .attributeModifiers(SwordItem.createAttributeModifiers(ModToolMaterials.LYCORITE, 3, 2.5f))
    ));
    public static final Item LYCORITE_PICKAXE = registerItem("lycorite_pickaxe", new PickaxeItem(ModToolMaterials.LYCORITE, new Item.Settings()
            .attributeModifiers(PickaxeItem.createAttributeModifiers(ModToolMaterials.LYCORITE, 3, 2.5f))
    ));
    public static final Item LYCORITE_SHOVEL = registerItem("lycorite_shovel", new ShovelItem(ModToolMaterials.LYCORITE, new Item.Settings()
            .attributeModifiers(ShovelItem.createAttributeModifiers(ModToolMaterials.LYCORITE, 3, -2.5f))
    ));
    public static final Item LYCORITE_AXE = registerItem("lycorite_axe", new AxeItem(ModToolMaterials.LYCORITE, new Item.Settings()
            .attributeModifiers(AxeItem.createAttributeModifiers(ModToolMaterials.LYCORITE, 3, -3.0f))
    ));
    public static final Item LYCORITE_HOE = registerItem("lycorite_hoe", new HoeItem(ModToolMaterials.LYCORITE, new Item.Settings()
            .attributeModifiers(HoeItem.createAttributeModifiers(ModToolMaterials.LYCORITE, 3, -3.0F))
    ));


    // Special Items
    public static final Item SANGUINE_SOLILOQUY = registerItem("sanguine_soliloquy", new SanguineSwordItem(ModToolMaterials.LYCORITE, new Item.Settings()
            .attributeModifiers(SwordItem.createAttributeModifiers(ModToolMaterials.LYCORITE, 5, 1.5f))
    ));
    public static final Item MEMENTO_MORI = registerItem("memento_mori", new MementoMoriItem(ToolMaterials.NETHERITE, new Item.Settings()
            .attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 6, -3.0f))
    ));

    // New Enchantments

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(LycorisAwakening.MOD_ID, name), item);
    }

    public static void registerModItems() {
        LycorisAwakening.LOGGER.info("Registering Mod Items for " + LycorisAwakening.MOD_ID);

    }
}
