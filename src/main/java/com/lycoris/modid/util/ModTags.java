package com.lycoris.modid.util;

import com.lycoris.modid.LycorisAwakening;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class ModTags {

    public static class Blocks {
        public static final TagKey<Block> NEEDS_LYCORITE_TOOL = createTag("needs_lycorite_tool");
        public static final TagKey<Block> INCORRECT_FOR_LYCORITE_TOOL = createTag("incorrect_for_lycorite_tool");

        private static TagKey<Block> createTag(String name){
            return TagKey.of(RegistryKeys.BLOCK, Identifier.of(LycorisAwakening.MOD_ID, name));
        }
    }

    public static class Items {

        private static TagKey<Item> createTag(String name){
            return TagKey.of(RegistryKeys.ITEM, Identifier.of(LycorisAwakening.MOD_ID, name));
        }
    }
}
