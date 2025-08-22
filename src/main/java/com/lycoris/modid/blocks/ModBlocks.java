package com.lycoris.modid.blocks;

import com.lycoris.modid.LycorisAwakening;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {

    // Building Blocks
    public static final Block ARCANUM_ALLOY_BLOCK = registerBlock("arcanum_alloy_block", new Block(AbstractBlock.Settings.create()
            .strength(4f)
            .requiresTool()
            .sounds(BlockSoundGroup.METAL)
    ));

    public static final Block BLIGHTWOOD_LOG = registerBlock("blightwood_log", new Block(AbstractBlock.Settings.create()
            .strength(4f)
    ));

    public static final Block BLIGHTWOOD_PLANKS = registerBlock("blightwood_log", new Block(AbstractBlock.Settings.create()
            .strength(4f)
    ));

    // Natural Blocks
    public static final Block LYCORITE_ORE = registerBlock("lycorite_ore", new Block(AbstractBlock.Settings.create()
            .strength(4f)
            .requiresTool()
            .sounds(BlockSoundGroup.SOUL_SOIL)

    ));

    // Block Entities
    // -- Arcane Enchanting Table
    // -- Glyphing Table
    // -- Arcanum Charging Station
    // -- Forte Table

    private static Block registerBlock(String name, Block block){
        registerBlockItem(name, block);
        return Registry.register(Registries.BLOCK, Identifier.of(LycorisAwakening.MOD_ID, name), block);
    }

    private static void registerBlockItem(String name, Block block){
        Registry.register(Registries.ITEM, Identifier.of(LycorisAwakening.MOD_ID, name),
                new BlockItem(block, new Item.Settings()));
    }

    public static void registerModBlocks() {
        LycorisAwakening.LOGGER.info("Registering Mod Blocks for " + LycorisAwakening.MOD_ID);


    }
}
