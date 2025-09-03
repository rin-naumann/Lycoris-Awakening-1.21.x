package com.lycoris.awakening.mixin;

import com.lycoris.awakening.client.WeatherClientHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public class SanguineMoonSkyMixin {

    @Unique
    private static final Identifier SANGUINE_MOON_TEXTURE =
            Identifier.of("lycoris-awakening", "textures/environment/sanguine_moon.png");

    /* ---------------- SKY COLOR ---------------- */
    @ModifyVariable(
            method = "renderSky",
            at = @At("STORE"),
            ordinal = 0 // the Vec3d sky color
    )
    private Vec3d modifySkyColor(Vec3d original, Matrix4f modelMatrix, Matrix4f projMatrix,
                                 float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback) {
        if (!WeatherClientHandler.isSanguineMoon()) return original;

        // Deep blood red
        double targetR = 0.35;
        double targetG = 0.0;
        double targetB = 0.0;

        float blend = 1.0f; // full override
        double r = MathHelper.lerp(blend, original.x, targetR);
        double g = MathHelper.lerp(blend, original.y, targetG);
        double b = MathHelper.lerp(blend, original.z, targetB);

        return new Vec3d(r, g, b);
    }

    /* ---------------- CLOUDS ---------------- */
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void cancelClouds(MatrixStack matrices,
                              Matrix4f modelMatrix,
                              Matrix4f projectionMatrix,
                              float tickDelta,
                              double cameraX,
                              double cameraY,
                              double cameraZ,
                              CallbackInfo ci) {
        if (WeatherClientHandler.isSanguineMoon()) {
            ci.cancel(); // no clouds during sanguine moon
        }
    }

    /* -------- Replace Sun with Sanguine Texture -------- */
    @Redirect(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/util/Identifier;)V",
                    ordinal = 0 // sun = first texture bound
            )
    )
    private void replaceSunWithSanguine(int texture, Identifier id) {
        if (WeatherClientHandler.isSanguineMoon()) {
            RenderSystem.setShaderTexture(texture, SANGUINE_MOON_TEXTURE);
        } else {
            RenderSystem.setShaderTexture(texture, id);
        }
    }
}
