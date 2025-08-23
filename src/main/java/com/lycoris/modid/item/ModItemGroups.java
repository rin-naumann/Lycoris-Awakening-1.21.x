package com.lycoris.modid.item;

import com.lycoris.modid.LycorisAwakening;
import com.lycoris.modid.blocks.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {

    public static final ItemGroup LYCORIS_AWAKENING_ITEMS_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(LycorisAwakening.MOD_ID, "lycoris_awakening_items"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModItems.LYCORITE))
                    .displayName(Text.translatable("itemgroup.lycoris-awakening.lycoris_awakening_items"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModItems.LYCORITE);
                        entries.add(ModItems.ARCANUM_ALLOY);
                        entries.add(ModItems.LYCORITE_PETAL);
                        entries.add(ModItems.BLOOD_DIAMOND);
                        entries.add(ModItems.LIFE_INFUSER);

                    }).build());

    public static final ItemGroup LYCORIS_AWAKENING_TOOLS_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(LycorisAwakening.MOD_ID, "lycoris_awakening_blocks"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModItems.SANGUINE_SOLILOQUY))
                    .displayName(Text.translatable("itemgroup.lycoris-awakening.lycoris_awakening_tools"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModItems.LYCORITE_SWORD);
                        entries.add(ModItems.SANGUINE_SOLILOQUY);
                        entries.add(ModItems.LYCORITE_AXE);
                        entries.add(ModItems.LYCORITE_PICKAXE);
                        entries.add(ModItems.LYCORITE_SHOVEL);
                        entries.add(ModItems.LYCORITE_HOE);
                        entries.add(ModItems.MEMENTO_MORI);

                    }).build());

    public static final ItemGroup LYCORIS_AWAKENING_BLOCKS_GROUP = Registry.register(Registries.ITEM_GROUP,
            Identifier.of(LycorisAwakening.MOD_ID, "lycoris_awakening_blocks"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModBlocks.ARCANUM_ALLOY_BLOCK))
                    .displayName(Text.translatable("itemgroup.lycoris-awakening.lycoris_awakening_blocks"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.ARCANUM_ALLOY_BLOCK);
                        entries.add(ModBlocks.LYCORITE_ORE);

                    }).build());

    public static void registerItemGroups(){
        LycorisAwakening.LOGGER.info("Registering Item Groups for " + LycorisAwakening.MOD_ID);
    }
}
