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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Mixin(BlockSubLevelLiftProvider.class)
public interface BlockSubLevelLiftProviderMixin {

    /** 记录当前 tick 内已应用过风场的 SubLevel，防止多帆叠加 */
    Set<ServerSubLevel> WIND_APPLIED_THIS_TICK = new HashSet<>();
    AtomicLong LAST_WIND_TICK = new AtomicLong(-1L);

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
        long tick = subLevel.getLevel().getGameTime();
        if (tick != LAST_WIND_TICK.get()) {
            WIND_APPLIED_THIS_TICK.clear();
            LAST_WIND_TICK.set(tick);
        }
        if (!WIND_APPLIED_THIS_TICK.add(subLevel)) {
            return; // 本 tick 已为该 SubLevel 应用过风场，跳过
        }

        Pose3d pose = subLevel.logicalPose();
        Vector3d globalSamplePos = pose.transformPosition(BlockSubLevelLiftProvider.LIFT_POS, BlockSubLevelLiftProvider.TEMP);
        Vector3dc airVelocity = WindTunnelWindProvider.getWindVelocityAt(globalSamplePos, subLevel.getLevel());
        if (airVelocity != null) {
            BlockSubLevelLiftProvider.LIFT_VELO.sub(airVelocity);
        }
    }
}

