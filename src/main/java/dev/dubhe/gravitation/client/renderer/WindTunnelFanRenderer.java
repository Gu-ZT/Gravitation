package dev.dubhe.gravitation.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.dubhe.gravitation.block.entity.WindTunnelBlockEntity;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;

public class WindTunnelFanRenderer extends SafeBlockEntityRenderer<WindTunnelBlockEntity> {
    public WindTunnelFanRenderer(BlockEntityRendererProvider.Context context) {
    }

    protected void renderSafe(
        WindTunnelBlockEntity blockEntity,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int light,
        int overlay
    ) {
        if (blockEntity.getLevel() == null) return;
        if (VisualizationManager.supportsVisualization(blockEntity.getLevel())) {
            return;
        }
        Direction direction = blockEntity.getFacing();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.cutoutMipped());
        int lightBehind = LevelRenderer.getLightColor(
            blockEntity.getLevel(),
            blockEntity.getBlockPos().relative(direction.getOpposite())
        );
        int lightInFront = LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockPos().relative(direction));
        SuperByteBuffer shaftHalf = CachedBuffers.partialFacing(
            AllPartialModels.SHAFT_HALF,
            blockEntity.getBlockState(),
            direction.getOpposite()
        );
        SuperByteBuffer fanInner = CachedBuffers.partialFacing(
            AllPartialModels.ENCASED_FAN_INNER,
            blockEntity.getBlockState(),
            direction.getOpposite()
        );
        float time = AnimationTickHolder.getRenderTime(blockEntity.getLevel());
        float angle = time * blockEntity.getRenderedFanSpeed() * 3.0F / 10.0F % 360.0F;
        angle = angle / 180.0F * (float) Math.PI;
        shaftHalf.light(lightBehind).renderInto(poseStack, vertexConsumer);
        fanInner.light(lightInFront).rotateCentered(angle, Direction.get(AxisDirection.POSITIVE, direction.getAxis()))
            .renderInto(poseStack, vertexConsumer);
    }
}
