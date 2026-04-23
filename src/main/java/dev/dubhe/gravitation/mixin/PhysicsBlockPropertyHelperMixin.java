package dev.dubhe.gravitation.mixin;

import dev.dubhe.gravitation.block.RedstoneMassEnergyConverterBlock;
import dev.dubhe.gravitation.init.ModBlocks;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PhysicsBlockPropertyHelper.class, remap = false)
public abstract class PhysicsBlockPropertyHelperMixin {
    @Inject(method = "getMass", at = @At("HEAD"), cancellable = true)
    private static void getMass(BlockGetter level, BlockPos pos, BlockState state, CallbackInfoReturnable<Double> cir) {
        if (!state.is(ModBlocks.REDSTONE_QUALITY_ENERGY_CONVERTER)) return;
        cir.setReturnValue(RedstoneMassEnergyConverterBlock.getMass(level, pos, state));
        cir.cancel();
    }
}
