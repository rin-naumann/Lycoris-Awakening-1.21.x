package com.lycoris.modid.item.custom.dualblades;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class LucidityItem extends SwordItem {

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            // Forward the event to your DualBladeHandler
            DualBladeHandler.handleRightClick(user, hand);
        }

        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    public LucidityItem(ToolMaterial toolMaterial, Settings settings) {
        super(toolMaterial, settings);
    }
}
