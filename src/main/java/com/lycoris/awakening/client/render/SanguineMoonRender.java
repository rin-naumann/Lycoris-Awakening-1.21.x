package com.lycoris.awakening.client.render;

import com.lycoris.awakening.client.WeatherClientHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;

public class SanguineMoonRender {
    public static void init() {
        HudRenderCallback.EVENT.register((matrices, tickDelta) -> {
            if (WeatherClientHandler.isSanguineMoon()) {
                drawRedOverlay();
            }
        });
    }

    private static void drawRedOverlay() {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR
        );

        float alpha = 0.25f; // transparency

        // Fullscreen quad in screen space
        buffer.vertex(0,     height, 0).color(1f, 0f, 0f, alpha);
        buffer.vertex(width, height, 0).color(1f, 0f, 0f, alpha);
        buffer.vertex(width, 0,      0).color(1f, 0f, 0f, alpha);
        buffer.vertex(0,     0,      0).color(1f, 0f, 0f, alpha);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
}