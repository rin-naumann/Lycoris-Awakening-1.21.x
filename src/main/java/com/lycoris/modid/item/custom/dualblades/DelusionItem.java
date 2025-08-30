package com.lycoris.modid.item.custom.dualblades;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class DelusionItem extends SwordItem {

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            if (user.isSneaking()) {

            }
        }

        return TypedActionResult.pass(user.getStackInHand(hand));
    }



    public DelusionItem(ToolMaterial toolMaterial, Settings settings) {
        super(toolMaterial, settings);
    }
}
