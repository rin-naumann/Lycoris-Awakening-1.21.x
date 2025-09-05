package com.lycoris.awakening.client.render;

import com.lycoris.awakening.LycorisAwakening;
import com.lycoris.awakening.entity.custom.projectile.SanguineProjectileEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class SanguineProjectileRenderer extends EntityRenderer<SanguineProjectileEntity> {

    public SanguineProjectileRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public void render(SanguineProjectileEntity entity, float entityYaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        // Do nothing → entity is invisible, only particles render
    }

    @Override
    public Identifier getTexture(SanguineProjectileEntity entity) {
        // required override, but unused since we don’t render a texture
        return Identifier.of(LycorisAwakening.MOD_ID, "textures/entity/empty.png");
    }
}
