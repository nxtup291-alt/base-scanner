package com.basescanner.render;

import com.basescanner.config.CategorySettings;
import com.basescanner.config.ConfigManager;
import com.basescanner.config.ModConfig;
import com.basescanner.scan.AreaScanResult;
import com.basescanner.scan.Navigation;
import com.basescanner.scan.ScanArea;
import com.basescanner.scan.ScanCategory;
import com.basescanner.scan.ScanManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.state.property.Properties;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.function.Consumer;

/**
 * Draws the scan-area boundaries, per-category ESP outlines and the navigation
 * tracer as thin, translucent lines. Nothing is filled, so the world stays visible.
 */
public final class WorldOverlayRenderer {
    private WorldOverlayRenderer() {
    }

    public static void render(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        ModConfig cfg = ConfigManager.get();
        if (cfg.interfaceHidden || client.world == null || client.player == null) return;

        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();
        // In 1.21.4 the main-pass stack is the identity; the camera rotation lives in the global
        // model-view matrix, so vertices are emitted camera-relative and drawn with the global program.
        MatrixStack matrices = ctx.matrixStack();
        if (matrices == null) matrices = new MatrixStack();
        float tickDelta = ctx.tickCounter().getTickDelta(false);
        ScanManager mgr = ScanManager.get();
        ScanArea focused = mgr.focusedArea(client.player.getPos());
        ClientWorld world = client.world;
        final MatrixStack stack = matrices;

        if (cfg.showOutline && !mgr.areas().isEmpty()) {
            drawPass(stack, cam, cfg.outlineWidth, cfg.outlineSeeThrough, batch -> {
                for (ScanArea area : mgr.areas()) drawArea(batch, area, area == focused, cfg);
            });
        }

        drawPass(stack, cam, cfg.espWidth, cfg.espSeeThrough, batch -> {
            if (cfg.espEnabled || cfg.entityHighlight) {
                List<ScanArea> targets = cfg.espAllAreas ? mgr.areas()
                        : focused == null ? List.of() : List.of(focused);
                int[] budget = {cfg.maxEspBoxes};
                for (ScanArea area : targets) {
                    if (budget[0] <= 0) break;
                    drawEsp(batch, world, area, cfg, tickDelta, budget);
                }
            }
            drawNavigation(batch, client, camera, cam, tickDelta);
        });
    }

    private static void drawPass(MatrixStack matrices, Vec3d cam, float width, boolean seeThrough,
                                 Consumer<LineBatch> body) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        LineBatch batch = new LineBatch(buffer, matrices.peek(), cam);
        body.accept(batch);
        BuiltBuffer built = buffer.endNullable();
        if (built == null) return;

        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.lineWidth(width);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        if (seeThrough) {
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }

        BufferRenderer.drawWithGlobalProgram(built);

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1f);
    }

    private static void drawArea(LineBatch batch, ScanArea area, boolean focused, ModConfig cfg) {
        int rgb = focused ? cfg.focusedOutlineColor : cfg.outlineColor;
        int alpha = focused ? cfg.outlineAlpha : Math.max(10, cfg.outlineAlpha * 2 / 3);
        int color = (alpha << 24) | (rgb & 0xFFFFFF);
        double x1 = area.minX, z1 = area.minZ, x2 = area.maxX + 1, z2 = area.maxZ + 1;

        if (cfg.verticalMode == ModConfig.VerticalMode.LIMITED) {
            batch.box(x1, area.minY, z1, x2, area.maxY + 1, z2, color);
        } else {
            // Full-height scans would draw a 384-block column; draw a band around the storage instead.
            batch.box(x1, area.anchorMinY - 1, z1, x2, area.anchorMaxY + 4, z2, color);
        }
        // Ground-level square at the storage height makes the 20x20 footprint easy to read.
        int ground = (alpha / 2 << 24) | (rgb & 0xFFFFFF);
        batch.rect(x1, z1, x2, z2, area.anchorMinY + 0.02, ground);

        // Sweep line while a scan is running
        if (area.isScanning()) {
            double z = z1 + (z2 - z1) * area.progress();
            batch.line(x1, area.anchorMinY + 0.05, z, x2, area.anchorMinY + 0.05, z, 0xFFFFFFFF);
        }
    }

    private static void drawEsp(LineBatch batch, ClientWorld world, ScanArea area, ModConfig cfg, float tickDelta,
                                int[] budget) {
        for (ScanCategory cat : ScanCategory.values()) {
            CategorySettings s = cfg.get(cat);
            if (!s.enabled || !s.esp) continue;
            if (cat.entity ? !cfg.entityHighlight : !cfg.espEnabled) continue;
            AreaScanResult result = area.resultFor(cat);
            if (result == null) continue;
            AreaScanResult.CategoryResult cr = result.get(cat);
            if (cr == null) continue;
            int color = (cfg.espAlpha << 24) | (s.color & 0xFFFFFF);

            for (AreaScanResult.TypeEntry te : cr.types.values()) {
                if (cat.entity) {
                    for (int id : te.entityIds) {
                        if (budget[0]-- <= 0) return;
                        Entity e = world.getEntityById(id);
                        if (e == null || e.isRemoved()) continue;
                        Vec3d lerped = e.getLerpedPos(tickDelta);
                        Box bb = e.getBoundingBox().offset(lerped.subtract(e.getPos())).expand(0.02);
                        batch.box(bb, color);
                    }
                } else {
                    for (BlockPos pos : te.positions) {
                        if (budget[0]-- <= 0) return;
                        Box bb = new Box(pos).expand(0.002);
                        if (cat == ScanCategory.CHESTS) bb = chestBox(world, pos, bb);
                        batch.box(bb, color);
                    }
                }
            }
        }
    }

    /** Double chests are outlined as one box covering both halves. */
    private static Box chestBox(ClientWorld world, BlockPos pos, Box single) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)
                || !state.getProperties().contains(Properties.CHEST_TYPE)
                || state.get(Properties.CHEST_TYPE) == ChestType.SINGLE) {
            return single;
        }
        Direction toOther = ChestBlock.getFacing(state);
        BlockPos other = pos.offset(toOther);
        if (world.getBlockState(other).getBlock() instanceof ChestBlock) {
            return single.union(new Box(other).expand(0.002));
        }
        return single;
    }

    private static void drawNavigation(LineBatch batch, MinecraftClient client, Camera camera, Vec3d cam,
                                       float tickDelta) {
        if (!Navigation.active() || client.world == null) return;
        Vec3d target = Navigation.target();
        double t = client.world.getTime() + tickDelta;
        int alpha = (int) (150 + 100 * Math.sin(t * 0.25));
        int color = (Math.max(40, Math.min(255, alpha)) << 24) | (Navigation.color() & 0xFFFFFF);

        BlockPos block = Navigation.targetBlock();
        if (block != null) {
            batch.box(new Box(block).expand(0.06), color);
        } else if (Navigation.entityId() >= 0) {
            Entity e = client.world.getEntityById(Navigation.entityId());
            if (e != null) {
                Vec3d lerped = e.getLerpedPos(tickDelta);
                batch.box(e.getBoundingBox().offset(lerped.subtract(e.getPos())).expand(0.08), color);
                target = lerped.add(0, e.getHeight() / 2.0, 0);
            }
        }
        Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        Vec3d start = cam.add(look.multiply(1.5));
        batch.line(start.x, start.y, start.z, target.x, target.y, target.z, color);
    }
}
