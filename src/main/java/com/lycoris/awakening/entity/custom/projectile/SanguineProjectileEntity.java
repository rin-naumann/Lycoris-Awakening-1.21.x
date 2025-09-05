package com.lycoris.awakening.entity.custom.projectile;

import com.lycoris.awakening.item.custom.SanguineSwordItem;
import com.lycoris.awakening.entity.ModEntities;
import com.lycoris.awakening.effect.ModEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class SanguineProjectileEntity extends PersistentProjectileEntity {
    private static final double MAX_DISTANCE = 30.0;
    private Vec3d startPos;

    // new field: arc orientation
    private boolean verticalArc = false; // false = horizontal, true = vertical

    public SanguineProjectileEntity(EntityType<? extends SanguineProjectileEntity> type, World world) {
        super(type, world);
        this.pickupType = PickupPermission.DISALLOWED;
    }

    public SanguineProjectileEntity(World world, LivingEntity owner, boolean verticalArc) {
        super(ModEntities.SANGUINE_PROJECTILE, world);
        this.pickupType = PickupPermission.DISALLOWED;
        this.setOwner(owner);
        this.startPos = owner.getPos();
        this.verticalArc = verticalArc;
    }

    public void setVerticalArc(boolean verticalArc) {
        this.verticalArc = verticalArc;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.startPos == null) this.startPos = this.getPos();

        if (this.squaredDistanceTo(startPos) > MAX_DISTANCE * MAX_DISTANCE) {
            this.discard();
            return;
        }

        if (this.getWorld() instanceof ServerWorld sw) {
            Vec3d forward = this.getVelocity().normalize();
            Vec3d up = new Vec3d(0, 1, 0);

            Vec3d axis;
            if (verticalArc) {
                // vertical arc: cross forward with sideways = "upward plane"
                Vec3d sideways = forward.crossProduct(up).normalize();
                axis = forward.crossProduct(sideways).normalize();
            } else {
                // horizontal arc: sideways axis
                axis = forward.crossProduct(up).normalize();
            }

            double arcRadius = 1.5;
            int arcSteps = 12;

            for (int i = 0; i <= arcSteps; i++) {
                double lerp = (i / (double) arcSteps) * 2.0 - 1.0;
                Vec3d offset = axis.multiply(lerp * arcRadius);

                Vec3d particlePos = this.getPos().add(offset);

                sw.spawnParticles(
                        new DustParticleEffect(new Vector3f(0.8f, 0.0f, 0.0f), 1.2f),
                        particlePos.x, particlePos.y, particlePos.z,
                        2,
                        0.05, 0.05, 0.05, 0.01
                );
            }
        }
    }


    @Override
    protected void onEntityHit(EntityHitResult hitResult) {
        Entity hit = hitResult.getEntity();
        if (hit instanceof LivingEntity living) {
            Entity owner = this.getOwner();
            if (owner instanceof PlayerEntity player) {
                SanguineSwordItem.attackWithFullDamage(player, living, 1.0f);
                living.addStatusEffect(new StatusEffectInstance(ModEffects.ENSANGUINED, 60, 0, false, true, true));
            }
        }
        // pierce: do NOT discard() so projectile goes through multiple enemies
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        this.discard(); // stop on block collision
    }

    @Override
    protected ItemStack asItemStack() {
        return ItemStack.EMPTY; // no pickup
    }

    @Override
    protected ItemStack getDefaultItemStack() {
        return ItemStack.EMPTY;
    }

    @Override
    protected double getGravity() {
        return 0.0f;
    }
}
