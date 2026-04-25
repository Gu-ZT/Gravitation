package dev.dubhe.gravitation.block.entity;

import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import dev.dubhe.gravitation.block.WindTunnelMountBlock;
import dev.dubhe.gravitation.windtunnel.WindTunnelMountMeasurement;
import dev.dubhe.gravitation.windtunnel.WindTunnelMountService;
import dev.dubhe.gravitation.windtunnel.WindTunnelWindProvider;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;
import javax.annotation.Nullable;

public class WindTunnelMountBlockEntity extends SyncedBlockEntity {
    private static final double AXIS_EPSILON = 1.0E-6;
    private static final Vector3d WORLD_UP = new Vector3d(0.0F, 1.0F, 0.0F);
    private static final Vector3d WORLD_NORTH = new Vector3d(0.0F, 0.0F, -1.0F);
    private static final String BOUND_SUBLEVEL_KEY = "BoundSubLevel";
    private static final String INTERFACE_POS_KEY = "InterfacePos";
    private static final String INTERFACE_FACING_KEY = "InterfaceFacing";
    private static final String FLOW_DIRECTION_KEY = "FlowDirection";
    private static final String LOCKED_KEY = "Locked";
    private static final String OFFSET_X_KEY = "OffsetX";
    private static final String OFFSET_Y_KEY = "OffsetY";
    private static final String OFFSET_Z_KEY = "OffsetZ";
    private static final String ANGLE_OF_ATTACK_KEY = "AngleOfAttack";
    private static final String SIDESLIP_ANGLE_KEY = "SideslipAngle";
    private static final String MEASUREMENT_KEY = "Measurement";
    private static final String MEASUREMENT_LIFT_KEY = "Lift";
    private static final String MEASUREMENT_DRAG_KEY = "Drag";
    private static final String MEASUREMENT_SIDE_KEY = "Side";
    private static final String MEASUREMENT_PITCH_KEY = "Pitch";
    private static final String MEASUREMENT_ROLL_KEY = "Roll";
    private static final String MEASUREMENT_YAW_KEY = "Yaw";
    public static final double MIN_OFFSET = -64.0D;
    public static final double MAX_OFFSET = 64.0D;
    public static final double MIN_ANGLE = -90.0D;
    public static final double MAX_ANGLE = 90.0D;
    private static final long MEASUREMENT_SYNC_INTERVAL_TICKS = 2L;

    @Nullable
    private UUID boundSubLevelId;
    @Getter
    private BlockPos interfacePos = BlockPos.ZERO;
    @Getter
    private Direction interfaceFacing = Direction.NORTH;
    @Getter
    private Direction flowDirection = Direction.NORTH;
    private boolean locked;
    @Getter
    private double offsetX;
    @Getter
    private double offsetY;
    @Getter
    private double offsetZ;
    @Getter
    private double angleOfAttack;
    @Getter
    private double sideslipAngle;
    @Getter
    private WindTunnelMountMeasurement measurement = WindTunnelMountMeasurement.EMPTY;
    @Nullable
    private transient PhysicsConstraintHandle activeConstraint;
    @Nullable
    private transient UUID activeConstraintSubLevelId;
    @Getter
    private transient boolean poseDirty = true;
    private transient long lastMeasurementSyncTick = Long.MIN_VALUE;

    public WindTunnelMountBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            WindTunnelMountService.register(this);
        }
        // Re-evaluate the pose after chunk reload even if the saved data itself did not change.
        poseDirty = true;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel) {
            WindTunnelMountService.unregister(this);
        }
        releaseConstraint();
        super.setRemoved();
    }

    public boolean hasBinding() {
        return boundSubLevelId != null;
    }

    @Nullable
    public UUID getBoundSubLevelId() {
        return boundSubLevelId;
    }

    public Direction getMountFacing() {
        return getBlockState().getValue(WindTunnelMountBlock.FACING);
    }

    public boolean isLocked() {
        return locked && hasBinding();
    }

    public void bind(ServerLevel level, UUID subLevelId, BlockPos interfacePos, Direction interfaceFacing) {
        this.boundSubLevelId = subLevelId;
        this.interfacePos = interfacePos.immutable();
        this.interfaceFacing = interfaceFacing;
        Vector3d interfaceWorldCenter = resolveSampleWorldPosition(level, subLevelId, this.interfacePos);
        this.flowDirection = resolveInitialFlowDirection(level, interfaceWorldCenter, interfaceFacing);
        initializeOffsetsFromCurrentInterface(interfaceFacing, interfaceWorldCenter);
        this.measurement = WindTunnelMountMeasurement.EMPTY;
        this.poseDirty = true;
        BlockState state = getBlockState();
        if (state.getBlock() instanceof WindTunnelMountBlock && state.getValue(WindTunnelMountBlock.FACING) != interfaceFacing) {
            // Align the stand's facing to the interface so the local stand axes stay intuitive.
            level.setBlock(worldPosition, state.setValue(WindTunnelMountBlock.FACING, interfaceFacing), Block.UPDATE_CLIENTS);
        }
        setChanged();
        sendData();
    }

    public void clearBinding() {
        boundSubLevelId = null;
        interfacePos = BlockPos.ZERO;
        interfaceFacing = Direction.NORTH;
        locked = false;
        measurement = WindTunnelMountMeasurement.EMPTY;
        poseDirty = false;
        releaseConstraint();
        setChanged();
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    public void applySettings(
        boolean locked, Direction flowDirection, double angleOfAttack, double sideslipAngle,
        double offsetX, double offsetY, double offsetZ
    ) {
        boolean changed = this.locked != locked
                          || this.flowDirection != flowDirection
                          || Double.compare(this.angleOfAttack, clampAngle(angleOfAttack)) != 0
                          || Double.compare(this.sideslipAngle, clampAngle(sideslipAngle)) != 0
                          || Double.compare(this.offsetX, clampOffset(offsetX)) != 0
                          || Double.compare(this.offsetY, clampOffset(offsetY)) != 0
                          || Double.compare(this.offsetZ, clampOffset(offsetZ)) != 0;

        this.locked = locked;
        this.flowDirection = flowDirection;
        this.angleOfAttack = clampAngle(angleOfAttack);
        this.sideslipAngle = clampAngle(sideslipAngle);
        this.offsetX = clampOffset(offsetX);
        this.offsetY = clampOffset(offsetY);
        this.offsetZ = clampOffset(offsetZ);

        if (changed) {
            // The service consumes this flag during the next physics step and recomputes target
            // pose only when something geometric actually changed.
            poseDirty = true;
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    }

    public void clearPoseDirty() {
        poseDirty = false;
    }

    public void setMeasurement(WindTunnelMountMeasurement measurement) {
        if (this.measurement.nearlyEquals(measurement)) {
            return;
        }

        this.measurement = measurement;
        setChanged();
        if (level != null && !level.isClientSide) {
            long gameTime = level.getGameTime();
            // Force readings can change every physics step, so sync at a lower cadence to avoid
            // flooding clients while still feeling live in the UI.
            if (gameTime - lastMeasurementSyncTick >= MEASUREMENT_SYNC_INTERVAL_TICKS) {
                lastMeasurementSyncTick = gameTime;
                sendData();
            }
        }
    }

    public void clearMeasurement() {
        setMeasurement(WindTunnelMountMeasurement.EMPTY);
    }

    @Nullable
    public PhysicsConstraintHandle getActiveConstraint() {
        return activeConstraint;
    }

    public boolean hasValidConstraintFor(UUID subLevelId) {
        return activeConstraint != null
               && activeConstraint.isValid()
               && subLevelId.equals(activeConstraintSubLevelId);
    }

    public void setActiveConstraint(@Nullable PhysicsConstraintHandle activeConstraint, @Nullable UUID subLevelId) {
        this.activeConstraint = activeConstraint;
        this.activeConstraintSubLevelId = subLevelId;
    }

    public void releaseConstraint() {
        if (activeConstraint != null) {
            activeConstraint.remove();
        }
        activeConstraint = null;
        activeConstraintSubLevelId = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boundSubLevelId != null) {
            tag.putUUID(BOUND_SUBLEVEL_KEY, boundSubLevelId);
            tag.putLong(INTERFACE_POS_KEY, interfacePos.asLong());
            tag.putString(INTERFACE_FACING_KEY, interfaceFacing.getName());
        }
        tag.putString(FLOW_DIRECTION_KEY, flowDirection.getName());
        tag.putBoolean(LOCKED_KEY, locked);
        tag.putDouble(OFFSET_X_KEY, offsetX);
        tag.putDouble(OFFSET_Y_KEY, offsetY);
        tag.putDouble(OFFSET_Z_KEY, offsetZ);
        tag.putDouble(ANGLE_OF_ATTACK_KEY, angleOfAttack);
        tag.putDouble(SIDESLIP_ANGLE_KEY, sideslipAngle);
        CompoundTag measurementTag = new CompoundTag();
        measurementTag.putDouble(MEASUREMENT_LIFT_KEY, measurement.lift());
        measurementTag.putDouble(MEASUREMENT_DRAG_KEY, measurement.drag());
        measurementTag.putDouble(MEASUREMENT_SIDE_KEY, measurement.sideForce());
        measurementTag.putDouble(MEASUREMENT_PITCH_KEY, measurement.pitchMoment());
        measurementTag.putDouble(MEASUREMENT_ROLL_KEY, measurement.rollMoment());
        measurementTag.putDouble(MEASUREMENT_YAW_KEY, measurement.yawMoment());
        tag.put(MEASUREMENT_KEY, measurementTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boundSubLevelId = tag.hasUUID(BOUND_SUBLEVEL_KEY) ? tag.getUUID(BOUND_SUBLEVEL_KEY) : null;
        if (boundSubLevelId != null && tag.contains(INTERFACE_POS_KEY)) {
            interfacePos = BlockPos.of(tag.getLong(INTERFACE_POS_KEY));
            interfaceFacing = readDirection(tag.getString(INTERFACE_FACING_KEY), Direction.NORTH);
        } else {
            interfacePos = BlockPos.ZERO;
            interfaceFacing = Direction.NORTH;
        }

        flowDirection = readDirection(tag.getString(FLOW_DIRECTION_KEY), Direction.NORTH);
        locked = tag.getBoolean(LOCKED_KEY);
        offsetX = clampOffset(tag.getDouble(OFFSET_X_KEY));
        offsetY = clampOffset(tag.getDouble(OFFSET_Y_KEY));
        offsetZ = clampOffset(tag.getDouble(OFFSET_Z_KEY));
        angleOfAttack = clampAngle(tag.getDouble(ANGLE_OF_ATTACK_KEY));
        sideslipAngle = clampAngle(tag.getDouble(SIDESLIP_ANGLE_KEY));

        if (tag.contains(MEASUREMENT_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag measurementTag = tag.getCompound(MEASUREMENT_KEY);
            measurement = new WindTunnelMountMeasurement(
                measurementTag.getDouble(MEASUREMENT_LIFT_KEY),
                measurementTag.getDouble(MEASUREMENT_DRAG_KEY),
                measurementTag.getDouble(MEASUREMENT_SIDE_KEY),
                measurementTag.getDouble(MEASUREMENT_PITCH_KEY),
                measurementTag.getDouble(MEASUREMENT_ROLL_KEY),
                measurementTag.getDouble(MEASUREMENT_YAW_KEY)
            );
        } else {
            measurement = WindTunnelMountMeasurement.EMPTY;
        }

        poseDirty = true;
    }

    private static double clampOffset(double value) {
        return Mth.clamp(value, MIN_OFFSET, MAX_OFFSET);
    }

    private static double clampAngle(double value) {
        return Mth.clamp(value, MIN_ANGLE, MAX_ANGLE);
    }

    @SuppressWarnings("SameParameterValue")
    private static Direction readDirection(String name, Direction fallback) {
        Direction direction = Direction.byName(name);
        return direction != null ? direction : fallback;
    }

    private Direction resolveInitialFlowDirection(ServerLevel level, Vector3d samplePos, Direction fallbackFacing) {
        Vector3dc windVelocity = WindTunnelWindProvider.getWindVelocityAt(samplePos, level);
        if (windVelocity == null) {
            return fallbackFacing;
        }

        double absX = Math.abs(windVelocity.x());
        double absY = Math.abs(windVelocity.y());
        double absZ = Math.abs(windVelocity.z());
        double max = Math.max(absX, Math.max(absY, absZ));
        if (max <= 1.0E-6) {
            return fallbackFacing;
        }
        if (max == absX) {
            return windVelocity.x() >= 0.0F ? Direction.EAST : Direction.WEST;
        }
        if (max == absY) {
            return windVelocity.y() >= 0.0F ? Direction.UP : Direction.DOWN;
        }
        return windVelocity.z() >= 0.0F ? Direction.SOUTH : Direction.NORTH;
    }

    private Vector3d resolveSampleWorldPosition(ServerLevel level, UUID subLevelId, BlockPos storedPos) {
        Vector3d storedCenter = toCenter(storedPos);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return storedCenter;
        }
        SubLevel maybeSubLevel = container.getSubLevel(subLevelId);
        if (!(maybeSubLevel instanceof ServerSubLevel subLevel)) {
            return storedCenter;
        }

        BoundingBox3ic plotBounds = subLevel.getPlot().getBoundingBox();
        if (plotBounds != null && plotBounds.contains(storedCenter)) {
            return subLevel.logicalPose().transformPosition(storedCenter, new Vector3d());
        }
        return storedCenter;
    }

    private void initializeOffsetsFromCurrentInterface(Direction mountForward, Vector3d interfaceWorldCenter) {
        Vector3d delta = interfaceWorldCenter.sub(toCenter(this.worldPosition), new Vector3d());
        Basis basis = Basis.fromForward(mountForward);
        this.offsetX = clampOffset(delta.dot(basis.right()));
        this.offsetY = clampOffset(delta.dot(basis.up()));
        this.offsetZ = clampOffset(delta.dot(basis.forward()));
    }

    private static Vector3d toCenter(BlockPos pos) {
        return new Vector3d((double) pos.getX() + 0.5F, (double) pos.getY() + 0.5F, (double) pos.getZ() + 0.5F);
    }

    private record Basis(Vector3d forward, Vector3d up, Vector3d right) {
        private static Basis fromForward(Direction forwardDirection) {
            Vector3d forward = directionVector(forwardDirection).normalize();
            Vector3d upReference = Math.abs(forward.dot(WORLD_UP)) > 0.999
                                   ? new Vector3d(WORLD_NORTH)
                                   : new Vector3d(WORLD_UP);
            Vector3d right = new Vector3d(forward).cross(upReference);
            if (right.lengthSquared() <= AXIS_EPSILON) {
                right.set(1.0F, 0.0F, 0.0F);
            } else {
                right.normalize();
            }

            Vector3d up = new Vector3d(right).cross(forward);
            if (up.lengthSquared() <= AXIS_EPSILON) {
                up.set(WORLD_UP);
            } else {
                up.normalize();
            }

            return new Basis(forward, up, right);
        }
    }

    private static Vector3d directionVector(Direction direction) {
        return new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

}
