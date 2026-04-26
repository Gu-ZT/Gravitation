package dev.dubhe.gravitation.mixin;

import dev.dubhe.gravitation.client.diagram.ContraptionDiagramExporter;
import dev.dubhe.gravitation.client.diagram.DiagramScreenExportAccess;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.simulated_team.simulated.content.entities.diagram.DiagramConfig;
import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Unique
    private float gravitation$lastPartialTicks;

    @Inject(method = "init", at = @At("TAIL"))
    private void gravitation$addExportButton(CallbackInfo ci) {
        final int diagramX = this.width / 2 - DiagramScreen.DIAGRAM_TEXTURE.width / 2;
        final int diagramY = this.height / 2 - DiagramScreen.DIAGRAM_TEXTURE.height / 2;

        this.addRenderableWidget(Button.builder(
                Component.literal("PNG"),
                button -> ContraptionDiagramExporter.exportAll(this)
            )
            .bounds(diagramX + DiagramScreen.DIAGRAM_TEXTURE.width - 70, diagramY + 6, 36, 16)
            .build());
    }

    @Inject(method = "renderWindow", at = @At("HEAD"))
    private void gravitation$capturePartialTicks(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        this.gravitation$lastPartialTicks = partialTicks;
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


