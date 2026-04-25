package dev.dubhe.gravitation.mixin;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.dubhe.gravitation.windtunnel.WindTunnelWindProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin({BlockSubLevelLiftProvider.class})
public interface BlockSubLevelLiftProviderMixin {
   @Overwrite
   default void sable$contributeLiftAndDrag(BlockSubLevelLiftProvider.LiftProviderContext ctx, ServerSubLevel subLevel, @NotNull Pose3d localPose, double timeStep, Vector3dc linearVelocity, Vector3dc angularVelocity, Vector3d linearImpulse, Vector3d angularImpulse, BlockSubLevelLiftProvider.@Nullable LiftProviderGroup group) {
      BlockSubLevelLiftProvider.resetVectors();
      BlockSubLevelLiftProvider.LIFT_NORMAL.set(ctx.dir().x(), ctx.dir().y(), ctx.dir().z());
      BlockSubLevelLiftProvider.LIFT_POS.set((double)ctx.pos().getX() + (double)0.5F, (double)ctx.pos().getY() + (double)0.5F, (double)ctx.pos().getZ() + (double)0.5F);
      if (localPose != null) {
         localPose.transformNormal(BlockSubLevelLiftProvider.LIFT_NORMAL);
         localPose.transformPosition(BlockSubLevelLiftProvider.LIFT_POS);
      }

      Pose3d pose = subLevel.logicalPose();
      Vector3d globalSamplePos = pose.transformPosition(BlockSubLevelLiftProvider.LIFT_POS, BlockSubLevelLiftProvider.TEMP);
      double pressure = DimensionPhysicsData.getAirPressure(subLevel.getLevel(), globalSamplePos);
      double localPointX = globalSamplePos.x - pose.position().x;
      double localPointY = globalSamplePos.y - pose.position().y;
      double localPointZ = globalSamplePos.z - pose.position().z;
      BlockSubLevelLiftProvider.DRAG.set(angularVelocity.y() * localPointZ - angularVelocity.z() * localPointY, angularVelocity.z() * localPointX - angularVelocity.x() * localPointZ, angularVelocity.x() * localPointY - angularVelocity.y() * localPointX);
      BlockSubLevelLiftProvider.LIFT_VELO.set(linearVelocity).add(BlockSubLevelLiftProvider.DRAG);
      Vector3dc airVelocity = WindTunnelWindProvider.getWindVelocityAt(globalSamplePos, subLevel.getLevel());
      if (airVelocity != null) {
         BlockSubLevelLiftProvider.LIFT_VELO.sub(airVelocity);
      }

      pose.transformNormalInverse(BlockSubLevelLiftProvider.LIFT_VELO);
      BlockSubLevelLiftProvider.LIFT_FORCE.zero();
      if (((BlockSubLevelLiftProvider)this).sable$getParallelDragScalar() > 0.0F) {
         double dragStrength = BlockSubLevelLiftProvider.LIFT_NORMAL.dot(BlockSubLevelLiftProvider.LIFT_VELO) * (double)((BlockSubLevelLiftProvider)this).sable$getParallelDragScalar() * pressure * timeStep;
         Vector3d parallelDrag = BlockSubLevelLiftProvider.LIFT_NORMAL.mul(dragStrength, BlockSubLevelLiftProvider.DRAG);
         BlockSubLevelLiftProvider.LIFT_FORCE.add(parallelDrag);
         if (group != null) {
            group.totalDrag().sub(parallelDrag);
            group.dragCenter().fma(Math.abs(dragStrength), BlockSubLevelLiftProvider.LIFT_POS);
            group.totalDragStrength += Math.abs(dragStrength);
         }
      }

      if (((BlockSubLevelLiftProvider)this).sable$getDirectionlessDragScalar() > 0.0F) {
         double dragStrength = (double)((BlockSubLevelLiftProvider)this).sable$getDirectionlessDragScalar() * pressure * timeStep;
         Vector3d directionlessDrag = BlockSubLevelLiftProvider.LIFT_VELO.mul(dragStrength, BlockSubLevelLiftProvider.TEMP);
         BlockSubLevelLiftProvider.LIFT_FORCE.add(directionlessDrag);
         if (group != null) {
            group.totalDrag().sub(directionlessDrag);
            group.dragCenter().fma(directionlessDrag.length(), BlockSubLevelLiftProvider.LIFT_POS);
            group.totalDragStrength += directionlessDrag.length();
         }
      }

      if (((BlockSubLevelLiftProvider)this).sable$getLiftScalar() > 0.0F) {
         double liftStrength = BlockSubLevelLiftProvider.LIFT_VELO.sub(BlockSubLevelLiftProvider.DRAG, BlockSubLevelLiftProvider.TEMP).length() * (double)((BlockSubLevelLiftProvider)this).sable$getLiftScalar() * pressure * timeStep;
         Vector3d lift = BlockSubLevelLiftProvider.LIFT_NORMAL.mul(liftStrength, BlockSubLevelLiftProvider.TEMP);
         BlockSubLevelLiftProvider.LIFT_FORCE.add(lift);
         if (group != null) {
            group.totalLift().sub(lift);
            group.liftCenter().fma(Math.abs(liftStrength), BlockSubLevelLiftProvider.LIFT_POS);
            group.totalLiftStrength += liftStrength;
         }
      }

      linearImpulse.sub(BlockSubLevelLiftProvider.LIFT_FORCE);
      BlockSubLevelLiftProvider.LIFT_POS.sub(subLevel.getMassTracker().getCenterOfMass(), BlockSubLevelLiftProvider.TEMP);
      angularImpulse.sub(BlockSubLevelLiftProvider.TEMP.cross(BlockSubLevelLiftProvider.LIFT_FORCE));
      BlockSubLevelLiftProvider.resetVectors();
   }
}
