package com.lycoris.awakening.mixin;

import com.lycoris.awakening.client.WeatherClientHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class SanguineMoonSkyMixin {

    private static final Identifier SANGUINE_MOON_TEXTURE =
            Identifier.of("lycoris-awakening", "textures/environment/sanguine_moon.png");

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void renderSanguineSky(Matrix4f modelMatrix,
                                   Matrix4f projectionMatrix,
                                   float tickDelta,
                                   Camera camera,
                                   boolean thickFog,
                                   Runnable fogCallback,
                                   CallbackInfo ci) {
        if (!WeatherClientHandler.isSanguineMoon()) return;

        // 🔴 Take over completely
        ci.cancel();
        fogCallback.run(); // still run fog so distance fog applies

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(modelMatrix);

        // 🔴 Draw a full dome (both above & below horizon)
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        // Colors (flipped)
        float topR = 0.0f, topG = 0.0f, topB = 0.0f;   // black (zenith)
        float botR = 0.3f, botG = 0.0f, botB = 0.0f;   // deep red (horizon + below)

        Matrix4f posMat = matrices.peek().getPositionMatrix();
        float size = 100.0f;

// North
        buffer.vertex(posMat, -size,  size, -size).color(topR, topG, topB, 1.0f);
        buffer.vertex(posMat, -size, -size, -size).color(botR, botG, botB, 1.0f);
        buffer.vertex(posMat,  size, -size, -size).color(botR, botG, botB, 1.0f);
        buffer.vertex(posMat,  size,  size, -size).color(topR, topG, topB, 1.0f);

// South
        buffer.vertex(posMat, -size,  size, size).color(topR, topG, topB, 1.0f);
        buffer.vertex(posMat,  size,  size, size).color(topR, topG, topB, 1.0f);
        buffer.vertex(posMat,  size, -size, size).color(botR, botG, botB, 1.0f);
        buffer.vertex(posMat, -size, -size, size).color(botR, botG, botB, 1.0f);

// East
        buffer.vertex(posMat, size,  size, -size).color(topR, topG, topB, 1.0f);
        buffer.vertex(posMat, size, -size, -size).color(botR, botG, botB, 1.0f);
        buffer.vertex(posMat, size, -size,  size).color(botR, botG, botB, 1.0f);
        buffer.vertex(posMat, size,  size,  size).color(topR, topG, topB, 1.0f);

// West
        buffer.vertex(posMat, -size,  size, -size).color(topR, topG, topB, 1.0f);
        buffer.vertex(posMat, -size,  size,  size).color(topR, topG, topB, 1.0f);
        buffer.vertex(posMat, -size, -size,  size).color(botR, botG, botB, 1.0f);
        buffer.vertex(posMat, -size, -size, -size).color(botR, botG, botB, 1.0f);

// Top
        buffer.vertex(posMat, -size, size, -size).color(topR, topG, topB, 1.0f);
        buffer.vertex(posMat,  size, size, -size).color(topR, topG, topB, 1.0f);
        buffer.vertex(posMat,  size, size,  size).color(topR, topG, topB, 1.0f);
        buffer.vertex(posMat, -size, size,  size).color(topR, topG, topB, 1.0f);

// Bottom
        buffer.vertex(posMat, -size, -size, -size).color(botR, botG, botB, 1.0f);
        buffer.vertex(posMat,  size, -size, -size).color(botR, botG, botB, 1.0f);
        buffer.vertex(posMat,  size, -size,  size).color(botR, botG, botB, 1.0f);
        buffer.vertex(posMat, -size, -size,  size).color(botR, botG, botB, 1.0f);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.depthMask(true);

        // 🔴 Draw custom "moon/sun" on top of dome
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, SANGUINE_MOON_TEXTURE);

        float texSize = 40.0f;
        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder texBuf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        texBuf.vertex(mat, -texSize, 100.0f, -texSize).texture(0.0f, 0.0f);
        texBuf.vertex(mat,  texSize, 100.0f, -texSize).texture(1.0f, 0.0f);
        texBuf.vertex(mat,  texSize, 100.0f,  texSize).texture(1.0f, 1.0f);
        texBuf.vertex(mat, -texSize, 100.0f,  texSize).texture(0.0f, 1.0f);

        BufferRenderer.drawWithGlobalProgram(texBuf.end());
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
            ci.cancel(); // no clouds during Sanguine Moon
        }
    }
}
