package dev.dubhe.gravitation.windtunnel;

import dev.dubhe.gravitation.block.entity.WindTunnelMountBlockEntity;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

public final class WindTunnelMountService {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE_MOUNTS = new ConcurrentHashMap<>();
    private static final Vector3d WORLD_UP = new Vector3d(0.0F, 1.0F, 0.0F);
    private static final Vector3d WORLD_NORTH = new Vector3d(0.0F, 0.0F, -1.0F);
    private static final double AXIS_EPSILON = 1.0E-6;

    private WindTunnelMountService() {
    }

    public static void register(WindTunnelMountBlockEntity mount) {
        Level var2 = mount.getLevel();
        if (!(var2 instanceof ServerLevel level)) {
            return;
        }
        ACTIVE_MOUNTS.computeIfAbsent(level.dimension(), (unused) -> ConcurrentHashMap.newKeySet())
            .add(mount.getBlockPos().immutable());
    }

    public static void unregister(WindTunnelMountBlockEntity mount) {
        Level var2 = mount.getLevel();
        if (!(var2 instanceof ServerLevel level)) {
            return;
        }
        Set<BlockPos> positions = ACTIVE_MOUNTS.get(level.dimension());
        if (positions == null) {
            return;
        }
        positions.remove(mount.getBlockPos());
        if (positions.isEmpty()) {
            ACTIVE_MOUNTS.remove(level.dimension());
        }
    }

    public static void prePhysicsTick(SubLevelPhysicsSystem system, double partialPhysicsTick) {
        ServerLevel level = system.getLevel();
        Set<BlockPos> positions = ACTIVE_MOUNTS.get(level.dimension());
        if (positions != null && !positions.isEmpty()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                for (BlockPos pos : new ArrayList<>(positions)) {
                    WindTunnelMountBlockEntity mount = getMount(level, pos, positions);
                    if (mount != null) {
                        if (!mount.hasBinding()) {
                            mount.releaseConstraint();
                            mount.clearMeasurement();
                        } else {
                            UUID subLevelId = mount.getBoundSubLevelId();
                            if (subLevelId == null) {
                                mount.releaseConstraint();
                                mount.clearMeasurement();
                            } else {
                                SubLevel var11 = container.getSubLevel(subLevelId);
                                if (var11 instanceof ServerSubLevel subLevel) {
                                    subLevel.enableIndividualQueuedForcesTracking(true);
                                    if (!mount.isLocked()) {
                                        mount.releaseConstraint();
                                        mount.clearMeasurement();
                                    } else {
                                        applyLockedPose(system, mount, subLevel);
                                        mount.clearPoseDirty();
                                    }
                                } else {
                                    mount.clearBinding();
                                    mount.clearMeasurement();
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    public static void postPhysicsTick(SubLevelPhysicsSystem system, double partialPhysicsTick) {
        ServerLevel level = system.getLevel();
        Set<BlockPos> positions = ACTIVE_MOUNTS.get(level.dimension());
        if (positions != null && !positions.isEmpty()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                for (BlockPos pos : new ArrayList<>(positions)) {
                    WindTunnelMountBlockEntity mount = getMount(level, pos, positions);
                    if (mount != null && mount.hasBinding() && mount.isLocked()) {
                        UUID subLevelId = mount.getBoundSubLevelId();
                        if (subLevelId != null) {
                            SubLevel var11 = container.getSubLevel(subLevelId);
                            if (var11 instanceof ServerSubLevel) {
                                ServerSubLevel subLevel = (ServerSubLevel) var11;
                                PoseSolution pose = computePoseSolution(mount, subLevel);
                                WindTunnelMountMeasurement measurement = computeMeasurement(mount, subLevel, pose);
                                mount.setMeasurement(measurement);
                            }
                        }
                    }
                }

            }
        }
    }

    private static void applyLockedPose(SubLevelPhysicsSystem system, WindTunnelMountBlockEntity mount, ServerSubLevel subLevel) {
        PoseSolution pose = computePoseSolution(mount, subLevel);
        mount.releaseConstraint();
        system.getPipeline().teleport(subLevel, pose.position(), pose.orientation());
        system.getPipeline().resetVelocity(subLevel);
        mount.setActiveConstraint(null, null);
    }

    private static WindTunnelMountMeasurement computeMeasurement(
        WindTunnelMountBlockEntity mount,
        ServerSubLevel subLevel,
        PoseSolution pose
    ) {
        Vector3d totalForce = new Vector3d();
        Vector3d totalMoment = new Vector3d();
        Vector3d interfaceAnchorLocal = pose.interfaceAnchorLocal();
        Object2ObjectMap<?, QueuedForceGroup> queuedForceGroups = subLevel.getQueuedForceGroups();
        if (queuedForceGroups == null) {
            return WindTunnelMountMeasurement.EMPTY;
        } else {
            accumulateGroup(
                queuedForceGroups.get(ForceGroups.LIFT.get()),
                interfaceAnchorLocal,
                totalForce,
                totalMoment
            );
            accumulateGroup(
                queuedForceGroups.get(ForceGroups.DRAG.get()),
                interfaceAnchorLocal,
                totalForce,
                totalMoment
            );
            Vector3d flowLocal = new Vector3d(pose.flowWorld());
            (new Quaterniond(pose.orientation())).transformInverse(flowLocal);
            flowLocal.normalize();
            Vector3d sideAxis = (new Vector3d(pose.bodyUpLocal())).cross(flowLocal);
            if (sideAxis.lengthSquared() <= AXIS_EPSILON) {
                sideAxis.set(pose.bodyRightLocal());
            } else {
                sideAxis.normalize();
            }

            Vector3d liftAxis = (new Vector3d(flowLocal)).cross(sideAxis);
            if (liftAxis.lengthSquared() <= AXIS_EPSILON) {
                liftAxis.set(pose.bodyUpLocal());
            } else {
                liftAxis.normalize();
            }

            return new WindTunnelMountMeasurement(
                totalForce.dot(liftAxis),
                totalForce.dot(flowLocal),
                totalForce.dot(sideAxis),
                totalMoment.dot(pose.bodyRightLocal()),
                totalMoment.dot(pose.bodyForwardLocal()),
                totalMoment.dot(pose.bodyUpLocal())
            );
        }
    }

    private static void accumulateGroup(@Nullable QueuedForceGroup group, Vector3d anchorLocal, Vector3d totalForce, Vector3d totalMoment) {
        if (group != null) {
            totalForce.add(group.getForceTotal().getLocalForce());

            for (QueuedForceGroup.PointForce pointForce : group.getRecordedPointForces()) {
                Vector3d point = new Vector3d(pointForce.point());
                Vector3d force = new Vector3d(pointForce.force());
                Vector3d arm = point.sub(anchorLocal, new Vector3d());
                totalMoment.add(arm.cross(force, new Vector3d()));
            }
        }
    }

    private static PoseSolution computePoseSolution(WindTunnelMountBlockEntity mount, ServerSubLevel subLevel) {
        Basis localBasis = Basis.fromForward(mount.getMountFacing());
        Basis worldBasis = Basis.forFlow(mount.getFlowDirection(), mount.getAngleOfAttack(), mount.getSideslipAngle());
        Quaterniond orientation = computeOrientation(localBasis, worldBasis);
        Vector3d interfaceAnchorLocal = resolveInterfaceAnchorLocal(mount, subLevel);
        Vector3d targetAnchorWorld = toCenter(mount.getBlockPos()).add((new Vector3d(localBasis.right())).mul(mount.getOffsetX()))
            .add((new Vector3d(localBasis.up())).mul(mount.getOffsetY()))
            .add((new Vector3d(localBasis.forward())).mul(mount.getOffsetZ()));
        Pose3d targetPose = new Pose3d(subLevel.logicalPose());
        targetPose.position().zero();
        targetPose.orientation().set(orientation);
        Vector3d anchorWithZeroTranslation = targetPose.transformPosition(interfaceAnchorLocal, new Vector3d());
        Vector3d position = targetAnchorWorld.sub(anchorWithZeroTranslation, new Vector3d());
        return new PoseSolution(
            position,
            orientation,
            targetAnchorWorld,
            directionVector(mount.getFlowDirection()),
            interfaceAnchorLocal,
            localBasis.forward(),
            localBasis.up(),
            localBasis.right()
        );
    }

    private static Vector3d resolveInterfaceAnchorLocal(WindTunnelMountBlockEntity mount, ServerSubLevel subLevel) {
        Vector3d storedAnchor = toCenter(mount.getInterfacePos());
        BoundingBox3ic plotBounds = subLevel.getPlot().getBoundingBox();
        return plotBounds != null && plotBounds.contains(storedAnchor)
               ? storedAnchor
               : subLevel.logicalPose().transformPositionInverse(storedAnchor, new Vector3d());
    }

    private static Quaterniond computeOrientation(Basis localBasis, Basis worldBasis) {
        Quaterniond alignForward = (new Quaterniond()).rotationTo(localBasis.forward(), worldBasis.forward());
        Vector3d alignedUp = (new Vector3d(localBasis.up())).rotate(alignForward);
        Vector3d upProjected = projectOntoPlane(alignedUp, worldBasis.forward());
        Vector3d desiredUpProjected = projectOntoPlane(worldBasis.up(), worldBasis.forward());
        if (!(upProjected.lengthSquared() <= AXIS_EPSILON) && !(desiredUpProjected.lengthSquared() <= AXIS_EPSILON)) {
            upProjected.normalize();
            desiredUpProjected.normalize();
            double sin = worldBasis.forward().dot((new Vector3d(upProjected)).cross(desiredUpProjected));
            double cos = upProjected.dot(desiredUpProjected);
            double twistAngle = Math.atan2(sin, cos);
            Quaterniond twist = (new Quaterniond()).fromAxisAngleRad(worldBasis.forward(), twistAngle);
            return twist.mul(alignForward).normalize();
        } else {
            return alignForward.normalize();
        }
    }

    private static Vector3d projectOntoPlane(Vector3d vector, Vector3d normal) {
        return vector.sub((new Vector3d(normal)).mul(vector.dot(normal)), new Vector3d());
    }

    private static Vector3d toCenter(BlockPos pos) {
        return new Vector3d((double) pos.getX() + (double) 0.5F, (double) pos.getY() + (double) 0.5F, (double) pos.getZ() + (double) 0.5F);
    }

    private static Vector3d directionVector(Direction direction) {
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static @Nullable WindTunnelMountBlockEntity getMount(ServerLevel level, BlockPos pos, Set<BlockPos> positions) {
        BlockEntity var4 = level.getBlockEntity(pos);
        if (var4 instanceof WindTunnelMountBlockEntity mount) {
            return mount;
        } else {
            positions.remove(pos);
            return null;
        }
    }

    private record Basis(Vector3d forward, Vector3d up, Vector3d right) {
        private static Basis fromForward(Direction forwardDirection) {
            Vector3d forward = WindTunnelMountService.directionVector(forwardDirection).normalize();
            Vector3d upReference = Math.abs(forward.dot(WindTunnelMountService.WORLD_UP)) > 0.999
                                   ? new Vector3d(WindTunnelMountService.WORLD_NORTH)
                                   : new Vector3d(WindTunnelMountService.WORLD_UP);
            Vector3d right = (new Vector3d(forward)).cross(upReference);
            if (right.lengthSquared() <= AXIS_EPSILON) {
                right.set(1.0F, 0.0F, 0.0F);
            } else {
                right.normalize();
            }

            Vector3d up = (new Vector3d(right)).cross(forward);
            if (up.lengthSquared() <= AXIS_EPSILON) {
                up.set(WindTunnelMountService.WORLD_UP);
            } else {
                up.normalize();
            }

            return new Basis(forward, up, right);
        }

        private static Basis forFlow(Direction flowDirection, double angleOfAttack, double sideslipAngle) {
            Vector3d flow = WindTunnelMountService.directionVector(flowDirection).normalize();
            Vector3d forward = (new Vector3d(flow)).negate();
            Vector3d upReference = Math.abs(forward.dot(WindTunnelMountService.WORLD_UP)) > 0.999
                                   ? new Vector3d(WindTunnelMountService.WORLD_NORTH)
                                   : new Vector3d(WindTunnelMountService.WORLD_UP);
            Vector3d right = (new Vector3d(forward)).cross(upReference);
            if (right.lengthSquared() <= AXIS_EPSILON) {
                right.set(1.0F, 0.0F, 0.0F);
            } else {
                right.normalize();
            }

            Vector3d up = (new Vector3d(right)).cross(forward).normalize();
            Quaterniond betaRotation = (new Quaterniond()).fromAxisAngleRad(up, Math.toRadians(sideslipAngle));
            Quaterniond alphaRotation = (new Quaterniond()).fromAxisAngleRad(
                (new Vector3d(right)).rotate(betaRotation),
                Math.toRadians(angleOfAttack)
            );
            Quaterniond totalRotation = (new Quaterniond(alphaRotation)).mul(betaRotation);
            forward.rotate(totalRotation).normalize();
            up.rotate(totalRotation).normalize();
            right.rotate(totalRotation).normalize();
            return new Basis(forward, up, right);
        }
    }

    private record PoseSolution(
        Vector3d position,
        Quaterniond orientation,
        Vector3d constraintAnchorWorld,
        Vector3d flowWorld,
        Vector3d interfaceAnchorLocal,
        Vector3d bodyForwardLocal,
        Vector3d bodyUpLocal,
        Vector3d bodyRightLocal
    ) {
    }
}
