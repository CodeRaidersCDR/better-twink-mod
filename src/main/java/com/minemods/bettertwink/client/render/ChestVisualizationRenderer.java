package com.minemods.bettertwink.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import com.minemods.bettertwink.BetterTwinkMod;
import com.minemods.bettertwink.client.bot.SortingBotController;
import com.minemods.bettertwink.config.BetterTwinkConfig;
import com.minemods.bettertwink.data.ChestConfiguration;
import com.minemods.bettertwink.data.ConfigurationManager;
import com.minemods.bettertwink.data.ServerConfiguration;

import java.util.List;

/**
 * Renders:
 *  - Coloured bounding boxes around registered chests (green = normal, orange = quick-drop)
 *  - Yellow dots + cyan lines showing the bot's current A* path
 */
@Mod.EventBusSubscriber(modid = BetterTwinkMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ChestVisualizationRenderer {
    private static final Minecraft MC = Minecraft.getInstance();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (MC.player == null || MC.level == null) return;
        if (!BetterTwinkConfig.HIGHLIGHT_CHESTS.get()) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Vec3 cam      = MC.gameRenderer.getMainCamera().getPosition();
        PoseStack ps  = event.getPoseStack();

        // ── Translate to world origin (subtract camera position) ──
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        // ── Chest highlights ──────────────────────────────────────
        ServerConfiguration config = ConfigurationManager.getInstance().getCurrentServerConfig();
        config.getChests().forEach((posKey, chestConfig) -> {
            BlockPos pos = chestConfig.getPosition();
            if (MC.level != null && isValidChest(MC.level.getBlockState(pos).getBlock())) {
                float[] col = chestConfig.isQuickDrop()
                        ? new float[]{1.0f, 0.5f, 0.0f}   // orange
                        : new float[]{0.0f, 0.8f, 0.2f};  // green
                AABB box = new AABB(pos.getX() - 0.02, pos.getY() - 0.02, pos.getZ() - 0.02,
                                   pos.getX() + 1.02, pos.getY() + 1.02, pos.getZ() + 1.02);
                drawAABBLines(ps, box, col[0], col[1], col[2], 0.9f, 2.0f);
            }
        });

        // ── Bot path ──────────────────────────────────────────────
        List<BlockPos> path = SortingBotController.getInstance().getCurrentPath();
        if (path != null && path.size() > 1) {
            // Lines between nodes (cyan)
            for (int i = 0; i < path.size() - 1; i++) {
                Vec3 a = Vec3.atCenterOf(path.get(i));
                Vec3 b = Vec3.atCenterOf(path.get(i + 1));
                drawLine(ps, a.x, a.y + 0.1, a.z,
                             b.x, b.y + 0.1, b.z,
                             0.0f, 1.0f, 1.0f, 0.85f, 1.5f);
            }
            // Small boxes at each node (yellow)
            for (BlockPos node : path) {
                double cx = node.getX() + 0.5;
                double cy = node.getY();
                double cz = node.getZ() + 0.5;
                double r  = 0.12;
                AABB nBox = new AABB(cx - r, cy, cz - r, cx + r, cy + r * 2, cz + r);
                drawAABBLines(ps, nBox, 1.0f, 1.0f, 0.0f, 0.75f, 1.0f);
            }
        }

        // ── Target box (bright white) ─────────────────────────────
        BlockPos tgt = SortingBotController.getInstance().getCurrentTarget();
        if (tgt != null) {
            AABB tBox = new AABB(tgt.getX() - 0.06, tgt.getY() - 0.06, tgt.getZ() - 0.06,
                                 tgt.getX() + 1.06, tgt.getY() + 1.06, tgt.getZ() + 1.06);
            drawAABBLines(ps, tBox, 1.0f, 1.0f, 1.0f, 1.0f, 2.5f);
        }

        ps.popPose();
    }

    // ──────────────────────────────────────────────────────────
    //  Drawing helpers
    // ──────────────────────────────────────────────────────────

    private static boolean isValidChest(net.minecraft.world.level.block.Block block) {
        return block == Blocks.CHEST
            || block == Blocks.TRAPPED_CHEST
            || block == Blocks.BARREL
            || block == Blocks.SHULKER_BOX;
    }

    /** Draw the 12 edges of an AABB. */
    private static void drawAABBLines(PoseStack ps, AABB b,
                                      float r, float g, float c, float a, float lineW) {
        double x0 = b.minX, y0 = b.minY, z0 = b.minZ;
        double x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;

        // bottom
        drawLine(ps, x0,y0,z0, x1,y0,z0, r,g,c,a, lineW);
        drawLine(ps, x1,y0,z0, x1,y0,z1, r,g,c,a, lineW);
        drawLine(ps, x1,y0,z1, x0,y0,z1, r,g,c,a, lineW);
        drawLine(ps, x0,y0,z1, x0,y0,z0, r,g,c,a, lineW);
        // top
        drawLine(ps, x0,y1,z0, x1,y1,z0, r,g,c,a, lineW);
        drawLine(ps, x1,y1,z0, x1,y1,z1, r,g,c,a, lineW);
        drawLine(ps, x1,y1,z1, x0,y1,z1, r,g,c,a, lineW);
        drawLine(ps, x0,y1,z1, x0,y1,z0, r,g,c,a, lineW);
        // verticals
        drawLine(ps, x0,y0,z0, x0,y1,z0, r,g,c,a, lineW);
        drawLine(ps, x1,y0,z0, x1,y1,z0, r,g,c,a, lineW);
        drawLine(ps, x1,y0,z1, x1,y1,z1, r,g,c,a, lineW);
        drawLine(ps, x0,y0,z1, x0,y1,z1, r,g,c,a, lineW);
    }

    /** Draw a single line segment using Minecraft's Tesselator. */
    private static void drawLine(PoseStack ps,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  float r, float g, float b, float a,
                                  float lineWidth) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(lineWidth);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        Matrix4f mat = ps.last().pose();

        buf.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        buf.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a).endVertex();
        tess.end();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }
}