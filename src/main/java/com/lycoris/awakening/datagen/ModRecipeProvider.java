package com.lycoris.awakening.datagen;

import com.lycoris.awakening.LycorisAwakening;
import com.lycoris.awakening.blocks.ModBlocks;
import com.lycoris.awakening.item.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.ShapelessRecipeJsonBuilder;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends FabricRecipeProvider {
    public ModRecipeProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void generate(RecipeExporter exporter) {

        //Arcanum Alloy
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.ARCANUM_ALLOY, 3)
                .input(Items.IRON_INGOT, 2)
                .input(Items.LAPIS_LAZULI, 2)
                .input(Items.AMETHYST_SHARD, 2)
                .criterion(hasItem(Items.IRON_INGOT), conditionsFromItem(Items.IRON_INGOT))
                .criterion(hasItem(Items.LAPIS_LAZULI), conditionsFromItem(Items.LAPIS_LAZULI))
                .criterion(hasItem(Items.AMETHYST_SHARD), conditionsFromItem(Items.AMETHYST_SHARD))
                .offerTo(exporter, Identifier.of(LycorisAwakening.MOD_ID, "arcanum_alloy_from_crafting"));

        //Arcanum Alloy Block
        offerReversibleCompactingRecipes(exporter, RecipeCategory.BUILDING_BLOCKS, ModItems.ARCANUM_ALLOY, RecipeCategory.MISC, ModBlocks.ARCANUM_ALLOY_BLOCK);

        //Lycorite Petal from Smelting
        List<ItemConvertible> PETAL_SMELTABLE = List.of(
                ModBlocks.LYCORITE_ORE
        );

        offerSmelting(exporter, PETAL_SMELTABLE, RecipeCategory.MISC, ModItems.LYCORITE_PETAL, 0.5f, 200, "lycorite_petal");
        offerBlasting(exporter, PETAL_SMELTABLE, RecipeCategory.MISC, ModItems.LYCORITE_PETAL, 0.5f, 100, "lycorite_petal");

        //Lycorite
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.LYCORITE, 1)
                .input(ModItems.BLOOD_DIAMOND, 4)
                .input(ModItems.LYCORITE_PETAL, 4)
                .criterion(hasItem(ModItems.BLOOD_DIAMOND), conditionsFromItem(ModItems.BLOOD_DIAMOND))
                .criterion(hasItem(ModItems.LYCORITE_PETAL), conditionsFromItem(ModItems.LYCORITE_PETAL))
                .offerTo(exporter);
    }
}
