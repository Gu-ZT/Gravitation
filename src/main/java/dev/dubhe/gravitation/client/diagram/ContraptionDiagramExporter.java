package dev.dubhe.gravitation.client.diagram;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ContraptionDiagramExporter {
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int SCENE_CLEAR_R = 46;
    private static final int SCENE_CLEAR_G = 48;
    private static final int SCENE_CLEAR_B = 50;
    private static final int SCENE_CLEAR_TOLERANCE = 2;

    private ContraptionDiagramExporter() {
    }

    public static void exportAll(DiagramScreenExportAccess access) {
        final Minecraft minecraft = Minecraft.getInstance();
        final Path outputDirectory = minecraft.gameDirectory.toPath().resolve("screenshots").resolve("gravitation");

        try {
            Files.createDirectories(outputDirectory);

            final String baseName = sanitizeFileName(access.gravitation$getDiagramName());
            final String timestamp = FILE_TIME_FORMAT.format(LocalDateTime.now());

            for (ExportPreset preset : ExportPreset.values()) {
                final String fileName = baseName + "_" + timestamp + "_" + preset.fileSuffix + ".png";
                exportSingle(access, preset, createUniqueFile(outputDirectory.resolve(fileName)));
            }

            notifyPlayer(Component.translatable(
                "gravitation.diagram.export.success",
                ExportPreset.values().length,
                outputDirectory.toString()
            ));
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
                stripSceneBackground(sceneOverlay);
                blendOnto(backgroundImage, sceneOverlay);

                // 3. 放大到目标输出分辨率后保存
                try (
                    NativeImage exportImage = scaleImage(backgroundImage, preset.width, preset.height);
                    NativeImage footerOverlay = renderFooterOverlay(minecraft, preset.width, preset.height, access.gravitation$getDiagramName())
                ) {
                    stripBlackBackground(footerOverlay);
                    blendOnto(exportImage, footerOverlay);
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

    // ---------- 场景渲染（Simulated DiagramScreen.draw） ----------

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
        DiagramScreen.draw(
            subLevel,
            partialTicks,
            localOrientation,
            projectionMatrix,
            cameraPosition,
            renderWidth,
            renderHeight,
            sceneFbo,
            outlineFbo,
            finalFbo,
            0.25f,
            1.0f,
            0x2E3032,
            0x696965
        );
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

    /** 直接从 GPU 纹理读回像素到 NativeImage */
    private static NativeImage readTexture(int textureId, int width, int height) {
        final NativeImage image = new NativeImage(width, height, false);
        RenderSystem.bindTexture(textureId);
        image.downloadTexture(0, true);
        image.flipY();
        return image;
    }

    /** 将 DiagramScreen 渲染的固定背景色替换为透明，以便叠加到羊皮纸上 */
    private static void stripSceneBackground(NativeImage scene) {
        for (int y = 0; y < scene.getHeight(); y++) {
            for (int x = 0; x < scene.getWidth(); x++) {
                final int pixel = scene.getPixelRGBA(x, y);
                final int red = FastColor.ABGR32.red(pixel);
                final int green = FastColor.ABGR32.green(pixel);
                final int blue = FastColor.ABGR32.blue(pixel);

                if (Math.abs(red - SCENE_CLEAR_R) <= SCENE_CLEAR_TOLERANCE
                    && Math.abs(green - SCENE_CLEAR_G) <= SCENE_CLEAR_TOLERANCE
                    && Math.abs(blue - SCENE_CLEAR_B) <= SCENE_CLEAR_TOLERANCE) {
                    scene.setPixelRGBA(x, y, FastColor.ABGR32.color(0, blue, green, red));
                }
            }
        }
    }

    /** 将透明目标读回后产生的纯黑背景转回透明，避免覆盖整张导出图。 */
    private static void stripBlackBackground(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                final int pixel = image.getPixelRGBA(x, y);
                final int alpha = FastColor.ABGR32.alpha(pixel);
                final int red = FastColor.ABGR32.red(pixel);
                final int green = FastColor.ABGR32.green(pixel);
                final int blue = FastColor.ABGR32.blue(pixel);

                if ((alpha == 0 || alpha == 255) && red <= 2 && green <= 2 && blue <= 2) {
                    image.setPixelRGBA(x, y, FastColor.ABGR32.color(0, blue, green, red));
                }
            }
        }
    }

    /** Alpha 混合：将 overlay 叠加到 destination 上 */
    private static void blendOnto(NativeImage destination, NativeImage overlay) {
        final int width = Math.min(destination.getWidth(), overlay.getWidth());
        final int height = Math.min(destination.getHeight(), overlay.getHeight());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int source = overlay.getPixelRGBA(x, y);
                final int sourceAlpha = FastColor.ABGR32.alpha(source);
                if (sourceAlpha == 0) continue;
                if (sourceAlpha == 255) {
                    destination.setPixelRGBA(x, y, source);
                    continue;
                }

                final int target = destination.getPixelRGBA(x, y);
                destination.setPixelRGBA(x, y, FastColor.ABGR32.color(
                    blendChannel(sourceAlpha, 255, FastColor.ABGR32.alpha(target)),
                    blendChannel(sourceAlpha, FastColor.ABGR32.blue(source), FastColor.ABGR32.blue(target)),
                    blendChannel(sourceAlpha, FastColor.ABGR32.green(source), FastColor.ABGR32.green(target)),
                    blendChannel(sourceAlpha, FastColor.ABGR32.red(source), FastColor.ABGR32.red(target))
                ));
            }
        }
    }

    private static int blendChannel(int srcA, int srcV, int dstV) {
        return (srcV * srcA + dstV * (255 - srcA)) / 255;
    }

    private static NativeImage renderFooterOverlay(Minecraft minecraft, int width, int height, String diagramName) {
        if (diagramName.isBlank()) {
            return new NativeImage(width, height, true);
        }

        final TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        boolean projectionBackedUp = false;
        boolean modelViewPushed = false;

        try {
            RenderSystem.backupProjectionMatrix();
            projectionBackedUp = true;

            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, width, height, 0.0F, 1000.0F, 21000.0F), VertexSorting.ORTHOGRAPHIC_Z);

            final Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            modelViewStack.pushMatrix();
            modelViewPushed = true;
            modelViewStack.identity();
            modelViewStack.translate(0.0F, 0.0F, -11000.0F);
            RenderSystem.applyModelViewMatrix();

            target.bindWrite(true);
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            target.clear(Minecraft.ON_OSX);
            RenderSystem.viewport(0, 0, width, height);

            final GuiGraphics graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            renderFooterName(graphics, minecraft, width, height, diagramName);
            graphics.flush();

            return readTexture(target.getColorTextureId(), width, height);
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

    private static void renderFooterName(GuiGraphics graphics, Minecraft minecraft, int width, int height, String diagramName) {
        final int footerWidth = minecraft.font.width(diagramName);
        final int padX = Math.max(6, minecraft.font.lineHeight / 2);
        final int padY = Math.max(4, minecraft.font.lineHeight / 3);
        final int rectW = footerWidth + padX * 2;
        final int rectH = minecraft.font.lineHeight + padY * 2;
        final int rectX = width - rectW - 16;
        final int rectY = height - rectH - 16;

        graphics.fill(
            rectX,
            rectY,
            rectX + rectW,
            rectY + rectH,
            DiagramScreen.BG_COLOR.getRGB()
        );
        graphics.drawString(
            minecraft.font,
            diagramName,
            rectX + padX,
            rectY + padY,
            DiagramScreen.TEXT_COLOR.getRGB(),
            false
        );
    }

    /** 最近邻放大：从 backgroundWidth×backgroundHeight 缩放到输出分辨率 */
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

    private enum ExportPreset {
        RATIO_16_9("16x9", 1920, 1080, 448, 252, "diagram16_9"),
        RATIO_4_3("4x3", 1920, 1440, 256, 192, "diagram4_3"),
        RATIO_3_2("3x2", 1920, 1280, 384, 256, "diagram3_2"),
        RATIO_1_1("1x1", 1920, 1920, 256, 256, "diagram1_1");

        private final String fileSuffix;
        private final int width;
        private final int height;
        private final int backgroundWidth;
        private final int backgroundHeight;
        private final ResourceLocation backgroundTexture;

        ExportPreset(String fileSuffix, int width, int height, int backgroundWidth, int backgroundHeight, String texturePath) {
            this.fileSuffix = fileSuffix;
            this.width = width;
            this.height = height;
            this.backgroundWidth = backgroundWidth;
            this.backgroundHeight = backgroundHeight;
            this.backgroundTexture = ResourceLocation.fromNamespaceAndPath("gravitation", "textures/gui/" + texturePath + ".png");
        }
    }
}

