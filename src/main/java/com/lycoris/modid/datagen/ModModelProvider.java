package com.lycoris.modid.datagen;

import com.lycoris.modid.blocks.ModBlocks;
import com.lycoris.modid.item.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.Models;

public class ModModelProvider extends FabricModelProvider {
    public ModModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {

        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.ARCANUM_ALLOY_BLOCK);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.LYCORITE_ORE);

    }

    @Override
    public void generateItemModels(ItemModelGenerator itemModelGenerator) {

        itemModelGenerator.register(ModItems.LYCORITE, Models.GENERATED);
        itemModelGenerator.register(ModItems.LYCORITE_PETAL, Models.GENERATED);
        itemModelGenerator.register(ModItems.ARCANUM_ALLOY, Models.GENERATED);
        itemModelGenerator.register(ModItems.BLOOD_DIAMOND, Models.GENERATED);
        itemModelGenerator.register(ModItems.LIFE_INFUSER, Models.GENERATED);

        itemModelGenerator.register(ModItems.LYCORITE_SWORD, Models.HANDHELD);
        itemModelGenerator.register(ModItems.SANGUINE_SOLILOQUY, Models.HANDHELD);
        itemModelGenerator.register(ModItems.MEMENTO_MORI, Models.HANDHELD);
        itemModelGenerator.register(ModItems.DELUSION, Models.HANDHELD);
        itemModelGenerator.register(ModItems.LUCIDITY, Models.HANDHELD);
    }
}
