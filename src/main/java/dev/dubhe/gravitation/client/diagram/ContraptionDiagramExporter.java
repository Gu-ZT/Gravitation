package dev.dubhe.gravitation.client.diagram;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.dubhe.gravitation.client.GravitationClient;
import dev.dubhe.gravitation.client.GravitationClientConfig;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import dev.simulated_team.simulated.util.SimpleSubLevelGroupRenderer;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Predicate;

public final class ContraptionDiagramExporter {
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final boolean FOOTER_OVERLAY_DEBUG_EXPORT = false;
    private static final boolean FOOTER_OVERLAY_RECT_TEST = false;
    // SCENE_CLEAR_* constants removed: background transparency is now preserved via
    // downloadTexture(0, false) and handled naturally by blendOnto()'s alpha check.
    private static final int FOOTER_KEY_R = 255;
    private static final int FOOTER_KEY_G = 0;
    private static final int FOOTER_KEY_B = 255;

    /**
     * Gravitation 专用的导出后处理管线（不含游戏内 UI 控件的遮挡区域切除）。
     * 文件路径：assets/gravitation/pinwheel/post/diagram_export.json
     */
    private static final ResourceLocation EXPORT_PIPELINE =
        ResourceLocation.fromNamespaceAndPath("gravitation", "diagram_export");

    private ContraptionDiagramExporter() {
    }

    public static void exportAll(DiagramScreenExportAccess access) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Path outputDirectory = minecraft.gameDirectory.toPath().resolve("screenshots").resolve("gravitation");

        try {
            Files.createDirectories(outputDirectory);

            final String baseName = sanitizeFileName(access.gravitation$getDiagramName());
            final String timestamp = FILE_TIME_FORMAT.format(LocalDateTime.now());

            int exported = 0;
            for (ExportPreset preset : ExportPreset.values()) {
                if (!preset.isEnabled()) continue;
                final String fileName = baseName + "_" + timestamp + "_" + preset.fileSuffix + ".png";
                exportSingle(access, preset, createUniqueFile(outputDirectory.resolve(fileName)));
                exported++;
            }

            if (exported == 0) {
                notifyPlayer(Component.translatable("gravitation.diagram.export.none_enabled"));
            } else {
                notifyPlayer(Component.translatable(
                    "gravitation.diagram.export.success",
                    exported,
                    outputDirectory.toString()
                ));
            }
        } catch (Exception exception) {
            notifyPlayer(Component.translatable("gravitation.diagram.export.failure", exception.getMessage()));
        }
    }

    private static void exportSingle(DiagramScreenExportAccess access, ExportPreset preset, Path outputFile) throws IOException {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientSubLevel subLevel = access.gravitation$getDiagramSubLevel();
        final DiagramConfig config = access.gravitation$getDiagramConfig();
        final float partialTicks = access.gravitation$getLastPartialTicks();
        // 场景及合成均在背景图尺寸下进行，最后再放大
        final int renderWidth = preset.backgroundWidth;
        final int renderHeight = preset.backgroundHeight;

        final AdvancedFbo sceneFbo = AdvancedFbo.withSize(renderWidth, renderHeight)
            .addColorTextureBuffer()
            .setDepthTextureBuffer()
            .build(true);
        final AdvancedFbo outlineFbo = AdvancedFbo.withSize(renderWidth, renderHeight)
            .addColorTextureBuffer()
            .build(true);
        final AdvancedFbo finalFbo = AdvancedFbo.withSize(renderWidth, renderHeight)
            .addColorTextureBuffer()
            .build(true);
        final RenderTarget mainTarget = minecraft.getMainRenderTarget();

        try {
            // 1. 用 Simulated 的方法在背景图尺寸 FBO 内渲染机械体场景
            renderDiagramScene(subLevel, config, partialTicks, renderWidth, renderHeight, sceneFbo, outlineFbo, finalFbo);

            // 2. 在背景图尺寸上做 CPU 合成（背景 + 场景叠加 + 文字标注）
            try (
                NativeImage backgroundImage = createBackgroundImage(minecraft, preset);
                NativeImage sceneOverlay = readTexture(finalFbo.getColorTextureAttachment(0).getId(), renderWidth, renderHeight)
            ) {
                blendOnto(backgroundImage, sceneOverlay);

                // 3. 放大到目标输出分辨率后保存
                final String diagramName = resolveFooterName(access, subLevel);
                try (NativeImage exportImage = scaleImage(backgroundImage, preset.width, preset.height)) {
                    if (!diagramName.isBlank()) {
                        final FooterLayout footerLayout = computeFooterLayout(minecraft, preset.width, preset.height, diagramName);
                        try (NativeImage footerOverlay = renderFooterOverlay(minecraft, footerLayout, diagramName)) {
                            if (FOOTER_OVERLAY_DEBUG_EXPORT) {
                                saveFooterOverlayDebug(footerOverlay, outputFile, "raw");
                            }
                            if (isFooterOverlayEmpty(footerOverlay)) {
                                drawFooterFallbackJava2D(exportImage, diagramName, preset.backgroundWidth, preset.backgroundHeight);
                            } else {
                                stripFooterKeyBackground(footerOverlay);
                                if (FOOTER_OVERLAY_DEBUG_EXPORT) {
                                    saveFooterOverlayDebug(footerOverlay, outputFile, "stripped");
                                }
                                blendOnto(exportImage, footerOverlay, footerLayout.x(), footerLayout.y());
                            }
                        }
                    }
                    exportImage.writeToFile(outputFile);
                }
            }
        } finally {
            mainTarget.bindWrite(true);
            RenderSystem.viewport(0, 0, mainTarget.width, mainTarget.height);

            finalFbo.free();
            outlineFbo.free();
            sceneFbo.free();
        }
    }

    // ---------- 场景渲染 ----------

    /**
     * 渲染机械体场景到指定 FBO。
     * <p>
     * 优先使用 Gravitation 自定义导出管线（{@link #EXPORT_PIPELINE}），
     * 该管线与 Simulated 的 diagram 管线完全一致，但 {@code sdScene()} 中
     * 移除了游戏内按钮列和旋转 Gizmo 的遮挡切除区域——这两处硬编码的
     * 屏幕像素坐标仅在游戏内渲染时需要避让 UI，导出时不存在任何覆盖其上
     * 的控件，切除区域会导致导出图中左上角等位置出现透明缺口。
     * <p>
     * 若 Veil 因某种原因未能加载导出管线，则回退至 {@link DiagramScreen#draw}。
     */
    private static void renderDiagramScene(
        ClientSubLevel subLevel,
        DiagramConfig config,
        float partialTicks,
        int renderWidth,
        int renderHeight,
        AdvancedFbo sceneFbo,
        AdvancedFbo outlineFbo,
        AdvancedFbo finalFbo
    ) {
        final float zNear = 0.1f;
        final LevelPlot plot = subLevel.getPlot();
        final BoundingBox3ic plotBounds = plot.getBoundingBox();

        float radius = Math.max(
            Math.max(plotBounds.maxX() - plotBounds.minX(), plotBounds.maxY() - plotBounds.minY()),
            plotBounds.maxZ() - plotBounds.minZ()
        ) + 1;
        radius *= 0.55F;
        radius = Math.max(radius, 2.0f);

        final Vector3d plotBoundsCenter = new Vector3d(
            (plotBounds.minX() + plotBounds.maxX() + 1) / 2.0,
            (plotBounds.minY() + plotBounds.maxY() + 1) / 2.0,
            (plotBounds.minZ() + plotBounds.maxZ() + 1) / 2.0
        );
        final float aspect = (float) renderWidth / renderHeight;
        final Matrix4f projectionMatrix = new Matrix4f().ortho(-radius * aspect, radius * aspect, -radius, radius, zNear, radius * 2.0f);
        final Quaternionf localOrientation = new Quaternionf()
            .rotateY((float) Math.toRadians(config.yaw()))
            .rotateX((float) Math.toRadians(config.pitch()));
        final Vector3d localCameraPosition = plotBoundsCenter.add(localOrientation.transform(new Vector3d(0, 0, radius)), new Vector3d());
        final Pose3dc renderPose = subLevel.renderPose(partialTicks);
        final Vector3d cameraPosition = new Vector3d(localCameraPosition);
        renderPose.transformPosition(cameraPosition);

        // 尝试使用导出专用管线（无按钮 / Gizmo 切除）
        final PostProcessingManager postManager = VeilRenderSystem.renderer().getPostProcessingManager();
        final PostPipeline exportPipeline = postManager.getPipeline(EXPORT_PIPELINE);

        if (exportPipeline != null) {
            // 1. 渲染几何体到 sceneFbo（与 DiagramScreen.draw 中完全相同的流程）
            final Quaternionf orientation = new Quaternionf(renderPose.orientation()).conjugate();
            orientation.premul(localOrientation.conjugate(new Quaternionf()));

            sceneFbo.bind(true);
            sceneFbo.clear();
            SimpleSubLevelGroupRenderer.renderChain(
                subLevel, sceneFbo, new Matrix4f(), projectionMatrix, cameraPosition, orientation, partialTicks
            );

            // 2. 配置导出管线 uniform（与 DiagramScreen.draw 使用相同值）
            final Color lineColor = new Color(0x2E3032);
            final Color lineShadowColor = new Color(0x696965);
            exportPipeline.getUniformSafe("LineColor").setVector(
                lineColor.getRed() / 255.0f, lineColor.getGreen() / 255.0f, lineColor.getBlue() / 255.0f, 1.0f
            );
            exportPipeline.getUniformSafe("LineShadowColor").setVector(
                lineShadowColor.getRed() / 255.0f, lineShadowColor.getGreen() / 255.0f, lineShadowColor.getBlue() / 255.0f, 1.0f
            );
            exportPipeline.getUniformSafe("InSize").setVector((float) renderWidth, (float) renderHeight);
            exportPipeline.getUniformSafe("PaletteOffset").setFloat(0.25f);
            exportPipeline.getUniformSafe("FadeScale").setFloat(1.0f);

            // 3. 绑定 FBO 并执行后处理（与 DiagramScreen.draw 使用相同的 simulated 命名空间 FBO 名称）
            final PostPipeline.Context context = postManager.getPostPipelineContext();
            context.setFramebuffer(ResourceLocation.fromNamespaceAndPath("simulated", "diagram"), sceneFbo);
            context.setFramebuffer(ResourceLocation.fromNamespaceAndPath("simulated", "diagram_outlined"), outlineFbo);
            context.setFramebuffer(ResourceLocation.fromNamespaceAndPath("simulated", "diagram_final"), finalFbo);
            postManager.runPipeline(exportPipeline, false);
        } else {
            // 回退：使用 Simulated 原始管线（含按钮 / Gizmo 切除，可能在左上角产生缺口）
            DiagramScreen.draw(
                subLevel, partialTicks, localOrientation, projectionMatrix, cameraPosition,
                renderWidth, renderHeight, sceneFbo, outlineFbo, finalFbo,
                0.25f, 1.0f, 0x2E3032, 0x696965
            );
        }
    }

    // ---------- CPU 合成工具方法 ----------

    /**
     * 从 Minecraft 资源管理器加载背景贴图。
     * 源贴图尺寸通常大于 backgroundWidth×backgroundHeight（如 512×256），
     * 实际有效内容只在左上角 backgroundWidth×backgroundHeight 区域内，
     * 其余部分为透明留白。因此直接裁剪左上角区域，不做整体缩放。
     */
    private static NativeImage createBackgroundImage(Minecraft minecraft, ExportPreset preset) throws IOException {
        try (
            InputStream stream = minecraft.getResourceManager().getResourceOrThrow(preset.backgroundTexture).open();
            NativeImage source = NativeImage.read(stream)
        ) {
            final int bw = preset.backgroundWidth;
            final int bh = preset.backgroundHeight;
            final NativeImage output = new NativeImage(bw, bh, true);
            for (int y = 0; y < bh; y++) {
                for (int x = 0; x < bw; x++) {
                    output.setPixelRGBA(x, y, source.getPixelRGBA(x, y));
                }
            }
            return output;
        }
    }

    /**
     * 直接从 GPU 纹理读回像素到 NativeImage。
     * 必须使用 opaque=false 以保留着色器输出的真实 alpha 通道。
     * outline_diagram.fsh 用 `alpha * dither_mask.r` 编码了背景透明度与
     * vignette 晕染效果；若用 opaque=true 会将所有像素强制为不透明，
     * 破坏边缘的 dithered 渐隐以及背景的透明。
     */
    private static NativeImage readTexture(int textureId, int width, int height) {
        final NativeImage image = new NativeImage(width, height, false);
        RenderSystem.bindTexture(textureId);
        image.downloadTexture(0, false);
        image.flipY();
        return image;
    }

    private static void blendOnto(NativeImage destination, NativeImage overlay) {
        blendOnto(destination, overlay, 0, 0);
    }

    /**
     * Alpha 混合：将 overlay 按指定偏移叠加到 destination 上
     */
    private static void blendOnto(NativeImage destination, NativeImage overlay, int offsetX, int offsetY) {
        final int width = Math.min(destination.getWidth(), overlay.getWidth());
        final int height = Math.min(destination.getHeight(), overlay.getHeight());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int targetX = x + offsetX;
                final int targetY = y + offsetY;
                if (targetX < 0 || targetY < 0 || targetX >= destination.getWidth() || targetY >= destination.getHeight()) {
                    continue;
                }

                final int source = overlay.getPixelRGBA(x, y);
                final int sourceAlpha = FastColor.ABGR32.alpha(source);
                if (sourceAlpha == 0) continue;
                if (sourceAlpha == 255) {
                    destination.setPixelRGBA(targetX, targetY, source);
                    continue;
                }

                final int target = destination.getPixelRGBA(targetX, targetY);
                destination.setPixelRGBA(
                    targetX, targetY, FastColor.ABGR32.color(
                        blendChannel(sourceAlpha, 255, FastColor.ABGR32.alpha(target)),
                        blendChannel(sourceAlpha, FastColor.ABGR32.blue(source), FastColor.ABGR32.blue(target)),
                        blendChannel(sourceAlpha, FastColor.ABGR32.green(source), FastColor.ABGR32.green(target)),
                        blendChannel(sourceAlpha, FastColor.ABGR32.red(source), FastColor.ABGR32.red(target))
                    )
                );
            }
        }
    }

    private static int blendChannel(int srcA, int srcV, int dstV) {
        return (srcV * srcA + dstV * (255 - srcA)) / 255;
    }

    private static void drawSolidRectDirect(int width, int height, int color) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        final BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        final Matrix4f matrix = new Matrix4f();
        builder.addVertex(matrix, 0.0f, 0.0f, 0.0f).setColor(color);
        builder.addVertex(matrix, 0.0f, height, 0.0f).setColor(color);
        builder.addVertex(matrix, width, height, 0.0f).setColor(color);
        builder.addVertex(matrix, width, 0.0f, 0.0f).setColor(color);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static NativeImage renderFooterOverlay(Minecraft minecraft, FooterLayout layout, String diagramName) {
        final TextureTarget target = new TextureTarget(layout.width(), layout.height(), false, Minecraft.ON_OSX);
        boolean projectionBackedUp = false;
        boolean modelViewPushed = false;

        try {
            RenderSystem.backupProjectionMatrix();
            projectionBackedUp = true;

            RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0F, layout.width(), layout.height(), 0.0F, -1.0F, 1.0F),
                VertexSorting.ORTHOGRAPHIC_Z
            );

            final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewPushed = true;
            modelViewStack.identity();
            RenderSystem.applyModelViewMatrix();

            target.bindWrite(true);
            target.setClearColor(1.0F, 0.0F, 1.0F, 1.0F);
            target.clear(Minecraft.ON_OSX);
            RenderSystem.viewport(0, 0, layout.width(), layout.height());
            // Reset potentially leaked state from previous scene rendering passes.
            RenderSystem.disableScissor();
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            if (FOOTER_OVERLAY_RECT_TEST) {
                // Diagnostic path: bypass GuiGraphics/font and draw an opaque rect directly.
                drawSolidRectDirect(layout.width(), layout.height(), 0xFFFFFFFF);
            } else {
                final MultiBufferSource.BufferSource overlayBuffer = MultiBufferSource.immediate(new ByteBufferBuilder(262144));
                final GuiGraphics graphics = new GuiGraphics(minecraft, overlayBuffer);
                renderFooterNameLocal(graphics, minecraft, layout, diagramName);
                graphics.flush();
            }
            RenderSystem.enableDepthTest();
            target.unbindWrite();
            return readTexture(target.getColorTextureId(), layout.width(), layout.height());
        } finally {
            if (modelViewPushed) {
                final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
                modelViewStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
            if (projectionBackedUp) {
                RenderSystem.restoreProjectionMatrix();
            }
            target.destroyBuffers();
        }
    }

    private static void stripFooterKeyBackground(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int pixel = image.getPixelRGBA(x, y);
                final int red = FastColor.ABGR32.red(pixel);
                final int green = FastColor.ABGR32.green(pixel);
                final int blue = FastColor.ABGR32.blue(pixel);
                if (red == FOOTER_KEY_R && green == FOOTER_KEY_G && blue == FOOTER_KEY_B) {
                    image.setPixelRGBA(x, y, FastColor.ABGR32.color(0, blue, green, red));
                }
            }
        }
    }

    private static boolean isFooterOverlayEmpty(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int pixel = image.getPixelRGBA(x, y);
                if (FastColor.ABGR32.red(pixel) != FOOTER_KEY_R
                    || FastColor.ABGR32.green(pixel) != FOOTER_KEY_G
                    || FastColor.ABGR32.blue(pixel) != FOOTER_KEY_B) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void drawFooterFallbackJava2D(NativeImage image, String diagramName, int bgWidth, int bgHeight) {
        final BufferedImage buffered = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int abgr = image.getPixelRGBA(x, y);
                final int argb = (FastColor.ABGR32.alpha(abgr) << 24)
                                 | (FastColor.ABGR32.red(abgr) << 16)
                                 | (FastColor.ABGR32.green(abgr) << 8)
                                 | FastColor.ABGR32.blue(abgr);
                buffered.setRGB(x, y, argb);
            }
        }

        final Graphics2D g = buffered.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            final int fontSize = Math.max(14, image.getWidth() / 15);
            g.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
            final var fm = g.getFontMetrics();

            final int textW = fm.stringWidth(diagramName);
            // 2px margin in background-image pixel space, scaled up to output resolution
            final int marginRight = Math.round(2.0f * image.getWidth() / bgWidth);
            final int marginBottom = Math.round(2.0f * image.getHeight() / bgHeight);
            final int rectW = textW + 4;  // 2px inner padding on each side
            final int rectH = fm.getAscent() + fm.getDescent() + 2;
            final int rectX = image.getWidth() - marginRight - rectW;
            final int rectY = image.getHeight() - marginBottom - rectH;

            g.setColor(new java.awt.Color(forceOpaque(DiagramScreen.BG_COLOR.getRGB()), true));
            g.fillRect(rectX, rectY, rectW, rectH);
            g.setColor(new java.awt.Color(forceOpaque(DiagramScreen.TEXT_COLOR.getRGB()), true));
            g.drawString(diagramName, rectX + 2, rectY + fm.getAscent());
        } finally {
            g.dispose();
        }

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int argb = buffered.getRGB(x, y);
                image.setPixelRGBA(
                    x, y, FastColor.ABGR32.color(
                        (argb >>> 24) & 0xFF,
                        argb & 0xFF,
                        (argb >>> 8) & 0xFF,
                        (argb >>> 16) & 0xFF
                    )
                );
            }
        }
    }

    private static FooterLayout computeFooterLayout(Minecraft minecraft, int width, int height, String diagramName) {
        final int footerWidth = minecraft.font.width(diagramName);
        final int rectX = width - footerWidth - 7;
        final int rectY = height - 5 - minecraft.font.lineHeight;
        final int rectW = footerWidth + 3;
        final int rectH = minecraft.font.lineHeight + 2;
        return new FooterLayout(rectX, rectY, rectW, rectH);
    }

    private static void renderFooterNameLocal(GuiGraphics graphics, Minecraft minecraft, FooterLayout layout, String diagramName) {
        graphics.fill(
            0,
            0,
            layout.width(),
            layout.height(),
            forceOpaque(DiagramScreen.BG_COLOR.getRGB())
        );
        graphics.drawString(
            minecraft.font,
            diagramName,
            2,
            2,
            forceOpaque(DiagramScreen.TEXT_COLOR.getRGB()),
            false
        );
    }

    private static int forceOpaque(int color) {
        return color | 0xFF000000;
    }

    /**
     * 最近邻放大：从 backgroundWidth×backgroundHeight 缩放到输出分辨率
     */
    private static NativeImage scaleImage(NativeImage source, int targetWidth, int targetHeight) {
        final NativeImage scaled = new NativeImage(targetWidth, targetHeight, true);
        final int srcW = source.getWidth();
        final int srcH = source.getHeight();
        for (int y = 0; y < targetHeight; y++) {
            final int sy = y * srcH / targetHeight;
            for (int x = 0; x < targetWidth; x++) {
                scaled.setPixelRGBA(x, y, source.getPixelRGBA(x * srcW / targetWidth, sy));
            }
        }
        return scaled;
    }

    private static String resolveFooterName(DiagramScreenExportAccess access, ClientSubLevel subLevel) {
        final String directName = access.gravitation$getDiagramName();
        if (!directName.isBlank()) {
            return directName;
        }

        final String subLevelName = subLevel.getName();
        return subLevelName == null ? "" : subLevelName;
    }

    private static void saveFooterOverlayDebug(NativeImage footerOverlay, Path outputFile, String stage) {
        try {
            final String fileName = outputFile.getFileName().toString();
            final int dot = fileName.lastIndexOf('.');
            final String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
            final Path debugPath = createUniqueFile(outputFile.getParent().resolve(stem + "_footer_overlay_" + stage + ".png"));
            footerOverlay.writeToFile(debugPath);
        } catch (IOException ignored) {
            // Debug export should never fail the main export path.
        }
    }

    // ---------- 通用工具 ----------

    private static String sanitizeFileName(String name) {
        final String base = name.isBlank() ? "contraption_diagram" : name;
        final String sanitized = base
            .replaceAll("[\\\\/:*?\"<>|]", "_")
            .replaceAll("\\s+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "contraption_diagram" : sanitized.toLowerCase(Locale.ROOT);
    }

    private static Path createUniqueFile(Path desiredFile) {
        if (!Files.exists(desiredFile)) return desiredFile;

        final String fileName = desiredFile.getFileName().toString();
        final int dotIndex = fileName.lastIndexOf('.');
        final String stem = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        final String extension = dotIndex >= 0 ? fileName.substring(dotIndex) : "";

        for (int index = 2; ; index++) {
            final Path candidate = desiredFile.getParent().resolve(stem + "_" + index + extension);
            if (!Files.exists(candidate)) return candidate;
        }
    }

    private static void notifyPlayer(Component message) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }

    private record FooterLayout(int x, int y, int width, int height) {
    }

    private enum ExportPreset {
        RATIO_16_9("16x9", 1920, 1080, 448, 252, "diagram16_9", cfg -> cfg.export16x9),
        RATIO_4_3("4x3", 1920, 1440, 256, 192, "diagram4_3", cfg -> cfg.export4x3),
        RATIO_3_2("3x2", 1920, 1280, 384, 256, "diagram3_2", cfg -> cfg.export3x2),
        RATIO_1_1("1x1", 1920, 1920, 256, 256, "diagram1_1", cfg -> cfg.export1x1);

        private final String fileSuffix;
        private final int width;
        private final int height;
        private final int backgroundWidth;
        private final int backgroundHeight;
        private final ResourceLocation backgroundTexture;
        private final Predicate<GravitationClientConfig.DiagramExportConfig> enabledGetter;

        ExportPreset(
            String fileSuffix, int width, int height, int backgroundWidth, int backgroundHeight,
            String texturePath, Predicate<GravitationClientConfig.DiagramExportConfig> enabledGetter
        ) {
            this.fileSuffix = fileSuffix;
            this.width = width;
            this.height = height;
            this.backgroundWidth = backgroundWidth;
            this.backgroundHeight = backgroundHeight;
            this.backgroundTexture = ResourceLocation.fromNamespaceAndPath("gravitation", "textures/gui/" + texturePath + ".png");
            this.enabledGetter = enabledGetter;
        }

        boolean isEnabled() {
            return enabledGetter.test(GravitationClient.CONFIG.diagramExport);
        }
    }
}

