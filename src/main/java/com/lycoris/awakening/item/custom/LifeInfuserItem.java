package com.lycoris.awakening.item.custom;

import com.lycoris.awakening.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class LifeInfuserItem extends Item {

    public LifeInfuserItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if(hand == Hand.MAIN_HAND) {
            ItemStack offHand = user.getOffHandStack();

            if (offHand.isOf(Items.DIAMOND)) {
                if(!world.isClient) {
                    user.setStackInHand(Hand.OFF_HAND,
                            new ItemStack(ModItems.BLOOD_DIAMOND, offHand.getCount()));

                    user.damage(world.getDamageSources().genericKill(), 5f);

                    world.playSound(user, user.getBlockPos(),
                            SoundEvents.ENTITY_PLAYER_HURT,
                            SoundCategory.PLAYERS,
                            1.0f, 1.0f);
                }
                return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
            }
        }
        return super.use(world, user, hand);
    }
}
