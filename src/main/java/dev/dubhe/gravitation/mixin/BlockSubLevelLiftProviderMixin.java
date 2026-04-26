package dev.dubhe.gravitation.mixin;

import dev.dubhe.gravitation.windtunnel.WindTunnelWindProvider;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockSubLevelLiftProvider.class)
public interface BlockSubLevelLiftProviderMixin {
    @Inject(
        method = "sable$contributeLiftAndDrag",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/companion/math/Pose3d;transformNormalInverse(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"
        ),
        require = 1
    )
    default void gravitation$applyWindField(
        BlockSubLevelLiftProvider.LiftProviderContext ctx,
        ServerSubLevel subLevel,
        Pose3d localPose,
        double timeStep,
        Vector3dc linearVelocity,
        Vector3dc angularVelocity,
        Vector3d linearImpulse,
        Vector3d angularImpulse,
        BlockSubLevelLiftProvider.@Nullable LiftProviderGroup group,
        CallbackInfo ci
    ) {
        Pose3d pose = subLevel.logicalPose();
        Vector3d globalSamplePos = pose.transformPosition(BlockSubLevelLiftProvider.LIFT_POS, BlockSubLevelLiftProvider.TEMP);
        Vector3dc airVelocity = WindTunnelWindProvider.getWindVelocityAt(globalSamplePos, subLevel.getLevel());
        if (airVelocity != null) {
            BlockSubLevelLiftProvider.LIFT_VELO.sub(airVelocity);
        }
    }
}
