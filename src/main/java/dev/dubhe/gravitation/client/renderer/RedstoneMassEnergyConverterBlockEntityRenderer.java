package dev.dubhe.gravitation.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.dubhe.gravitation.block.entity.RedstoneMassEnergyConverterBlockEntity;
import dev.dubhe.gravitation.client.init.ModPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class RedstoneMassEnergyConverterBlockEntityRenderer extends SafeBlockEntityRenderer<RedstoneMassEnergyConverterBlockEntity> {
    public RedstoneMassEnergyConverterBlockEntityRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(
        RedstoneMassEnergyConverterBlockEntity be,
        float partialTicks,
        PoseStack ms,
        MultiBufferSource buffer,
        int light,
        int overlay
    ) {
        Level level = be.getLevel();
        if (level == null) return;
        final VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());
        final BlockState state = be.getBlockState();
        final SuperByteBuffer inner = CachedBuffers.partial(ModPartialModels.REDSTONE_MASS_ENERGY_CONVERTER_INNER, state);
        this.transform(
            inner.rotateCentered(
                getFanAngle(partialTicks, level.getGameTime(), be.getMass()),
                Direction.Axis.Y
            )
        );
        inner.light(light).renderInto(ms, vb);
    }

    public float getFanAngle(final float pt, final long tick, final double speed) {
        double scale = 1.0d / 180.0d;
        double startAngle = ((tick - 1) * speed * scale) % 360;
        double endAngle = (tick * speed * scale) % 360;
        return (float) Mth.lerp(pt, startAngle, endAngle);
    }

    private void transform(final SuperByteBuffer diode) {
        diode.rotateCenteredDegrees(90, Direction.UP);
        diode.rotateCentered(Direction.UP.getRotation());
    }
}
