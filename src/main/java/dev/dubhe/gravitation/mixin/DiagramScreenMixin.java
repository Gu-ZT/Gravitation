package dev.dubhe.gravitation.mixin;

import dev.dubhe.gravitation.Gravitation;
import dev.dubhe.gravitation.client.diagram.ContraptionDiagramExporter;
import dev.dubhe.gravitation.client.diagram.DiagramScreenExportAccess;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import javax.annotation.Nullable;

@Mixin(DiagramScreen.class)
public abstract class DiagramScreenMixin extends Screen implements DiagramScreenExportAccess {
    protected DiagramScreenMixin() {
        super(Component.empty());
    }

    @Shadow
    protected DiagramConfig config;

    @Shadow
    @Final
    public ClientSubLevel subLevel;

    @Shadow
    @Final
    private static int TOOLTIP_LABEL_COLOR;
    @Unique
    private float gravitation$lastPartialTicks;

    @Unique
    private static final WidgetSprites GRAVITATION$BUTTON_SPRITES = new WidgetSprites(
        Gravitation.location("export_button"),
        Gravitation.location("export_button_hover")
    );

    @Nullable
    @Unique
    private ImageButton gravitation$exportButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void gravitation$addExportButton(CallbackInfo ci) {
        final int diagramX = this.width / 2 - DiagramScreen.DIAGRAM_TEXTURE.width / 2;
        final int diagramY = this.height / 2 - DiagramScreen.DIAGRAM_TEXTURE.height / 2;

        this.gravitation$exportButton = new ImageButton(
            diagramX + 9,
            diagramY + 9 + 20 * 4,
            16,
            16,
            GRAVITATION$BUTTON_SPRITES,
            button -> ContraptionDiagramExporter.exportAll(this)
        );

        this.addRenderableWidget(this.gravitation$exportButton);
    }

    @Inject(method = "renderWindow", at = @At("HEAD"))
    private void gravitation$capturePartialTicks(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        this.gravitation$lastPartialTicks = partialTicks;
    }

    @Inject(
        method = "renderWindowForeground",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;clear()V"
        )
    )
    private void gravitation$renderTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.gravitation$exportButton == null) return;
        if (!this.gravitation$exportButton.isHovered()) return;
        DiagramScreen.renderTooltip(
            graphics,
            mouseX,
            mouseY,
            List.of(
                Component.translatable("gravitation.diagram.export")
                    .withColor(TOOLTIP_LABEL_COLOR)
            )
        );
    }

    @Override
    public ClientSubLevel gravitation$getDiagramSubLevel() {
        return this.subLevel;
    }

    @Override
    public DiagramConfig gravitation$getDiagramConfig() {
        return this.config;
    }

    @Override
    public String gravitation$getDiagramName() {
        return this.subLevel.getName() == null ? "contraption_diagram" : this.subLevel.getName();
    }

    @Override
    public float gravitation$getLastPartialTicks() {
        return this.gravitation$lastPartialTicks;
    }
}


