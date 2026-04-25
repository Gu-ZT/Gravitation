package dev.dubhe.gravitation.block.entity;

import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.AirFlowParticleData;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import dev.dubhe.gravitation.block.WindTunnelBlock;
import dev.dubhe.gravitation.windtunnel.WindTunnelFlowField;
import dev.dubhe.gravitation.windtunnel.WindTunnelNetwork;
import dev.dubhe.gravitation.windtunnel.WindTunnelWindProvider;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class WindTunnelBlockEntity extends SyncedBlockEntity implements IAirCurrentSource {
    private static final String CONTROLLER_LENGTH_KEY = "ControllerLength";
    private static final String CONTROLLER_AIRSPEED_KEY = "ControllerAirspeed";
    private static final float MIN_PARTICLE_SPAWN_CHANCE = 0.15F;
    private static final float MAX_PARTICLE_SPAWN_CHANCE = 0.85F;
    @Getter
    private int controllerLength = 16;
    @Getter
    private double controllerAirspeed = 12.0F;
    private final AirCurrent airCurrent;
    private boolean airCurrentDirty = true;
    private Direction cachedFacing;
    private boolean cachedActive;
    private int cachedLength;
    private double cachedAirspeed;
    @Nullable
    private WindTunnelFlowField cachedFlowField;
    private AABB cachedSearchBounds;
    private boolean trackedActive;

    public WindTunnelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.cachedFacing = Direction.NORTH;
        this.cachedLength = -1;
        this.cachedAirspeed = -1.0F;
        this.airCurrent = new AirCurrent(this);
        this.cachedSearchBounds = new AABB(pos);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, WindTunnelBlockEntity blockEntity) {
        blockEntity.tickAirFlowParticles();
    }

    public boolean isActive() {
        BlockState state = this.getBlockState();
        return state.getBlock() instanceof WindTunnelBlock && state.getValue(WindTunnelBlock.POWERED);
    }

    public Direction getFacing() {
        return this.getBlockState().getValue(WindTunnelBlock.FACING);
    }

    public float getRenderedFanSpeed() {
        if (!this.isActive()) {
            return 0.0F;
        } else {
            float renderedSpeed = (float) this.controllerAirspeed * 4.0F;
            return renderedSpeed > 0.0F ? Mth.clamp(renderedSpeed, 80.0F, 1280.0F) : Mth.clamp(renderedSpeed, -1280.0F, -80.0F);
        }
    }

    public boolean matchesControllerSettings(int controllerLength, double controllerAirspeed) {
        return this.controllerLength == controllerLength && Double.compare(this.controllerAirspeed, controllerAirspeed) == 0;
    }

    public void applyControllerSettings(int controllerLength, double controllerAirspeed) {
        int clampedLength = Mth.clamp(controllerLength, 1, 64);
        double clampedAirspeed = Mth.clamp(controllerAirspeed, 0.0F, 64.0F);
        if (this.controllerLength != clampedLength || Double.compare(this.controllerAirspeed, clampedAirspeed) != 0) {
            this.controllerLength = clampedLength;
            this.controllerAirspeed = clampedAirspeed;
            this.markAirCurrentDirty();
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.refreshAirCurrentIfNeeded();
                this.sendData();
            }

        }
    }

    public AABB getSearchBounds() {
        this.refreshAirCurrentIfNeeded();
        return this.cachedSearchBounds;
    }

    @Nullable
    public WindTunnelFlowField getFlowField() {
        this.refreshAirCurrentIfNeeded();
        return this.cachedFlowField;
    }

    public void onTunnelStateChanged() {
        this.markAirCurrentDirty();
        this.refreshAirCurrentIfNeeded();
    }

    public void onLoad() {
        super.onLoad();
        this.markAirCurrentDirty();
        if (this.level != null && !this.level.isClientSide) {
            WindTunnelNetwork.refreshTunnel(this.level, this.worldPosition);
            this.refreshAirCurrentIfNeeded();
        }

    }

    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide) {
            WindTunnelWindProvider.unregister(this);
        }

        super.setRemoved();
    }

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(CONTROLLER_LENGTH_KEY, this.controllerLength);
        tag.putDouble(CONTROLLER_AIRSPEED_KEY, this.controllerAirspeed);
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(CONTROLLER_LENGTH_KEY)) {
            this.controllerLength = Mth.clamp(tag.getInt(CONTROLLER_LENGTH_KEY), 1, 64);
        }

        if (tag.contains(CONTROLLER_AIRSPEED_KEY)) {
            this.controllerAirspeed = Mth.clamp(tag.getDouble(CONTROLLER_AIRSPEED_KEY), 0.0F, 64.0F);
        }

        this.markAirCurrentDirty();
    }

    public AirCurrent getAirCurrent() {
        this.refreshAirCurrentIfNeeded();
        return this.airCurrent;
    }

    public Level getAirCurrentWorld() {
        return this.level;
    }

    public BlockPos getAirCurrentPos() {
        return this.worldPosition;
    }

    public float getSpeed() {
        return this.isActive() ? (float) this.controllerAirspeed : 0.0F;
    }

    public Direction getAirflowOriginSide() {
        return this.getFacing();
    }

    public Direction getAirFlowDirection() {
        return this.isActive() && this.controllerAirspeed > (double) 0.0F ? this.getFacing() : null;
    }

    public float getMaxDistance() {
        this.refreshAirCurrentIfNeeded();
        return this.airCurrent.maxDistance;
    }

    public boolean isSourceRemoved() {
        return this.isRemoved();
    }

    private void tickAirFlowParticles() {
        if (this.level != null && this.level.isClientSide) {
            AirCurrent current = this.getAirCurrent();
            if (!(current.maxDistance <= 0.0F) && current.direction != null) {
                float normalizedSpeed = (float) Mth.clamp(this.controllerAirspeed / (double) 64.0F, 0.0F, 1.0F);
                float spawnChance = Mth.lerp(normalizedSpeed, MIN_PARTICLE_SPAWN_CHANCE, MAX_PARTICLE_SPAWN_CHANCE);
                if (!(this.level.random.nextFloat() > spawnChance)) {
                    int particleCount = 1 + Mth.floor(normalizedSpeed * 2.0F);
                    Vec3 spawnPos = Vec3.atCenterOf(this.worldPosition)
                        .add(Vec3.atLowerCornerOf(current.direction.getNormal()).scale(0.5F));

                    for (int i = 0; i < particleCount; ++i) {
                        this.level.addParticle(
                            new AirFlowParticleData(this.worldPosition),
                            spawnPos.x,
                            spawnPos.y,
                            spawnPos.z,
                            0.0F,
                            0.0F,
                            0.0F
                        );
                    }

                }
            }
        }
    }

    private void refreshAirCurrentIfNeeded() {
        if (this.level == null) {
            this.clearAirCurrent();
        } else {
            Direction facing = this.getFacing();
            boolean active = this.isActive();
            if (this.airCurrentDirty || this.cachedFacing != facing || this.cachedActive != active || this.cachedLength != this.controllerLength || Double.compare(
                this.cachedAirspeed,
                this.controllerAirspeed
            ) != 0) {
                this.cachedFacing = facing;
                this.cachedActive = active;
                this.cachedLength = this.controllerLength;
                this.cachedAirspeed = this.controllerAirspeed;
                this.airCurrentDirty = false;
                if (active && !(this.controllerAirspeed <= (double) 0.0F)) {
                    WindTunnelFlowField field = WindTunnelFlowField.create(
                        this.level,
                        this.worldPosition,
                        facing,
                        this.controllerLength,
                        this.controllerAirspeed
                    );
                    if (field != null && !(field.length() <= (double) 0.0F)) {
                        this.cachedFlowField = field;
                        this.cachedSearchBounds = field.bounds();
                        if (!this.level.isClientSide) {
                            WindTunnelWindProvider.updateTracking(this, true);
                            this.trackedActive = true;
                        }

                        this.airCurrent.direction = field.direction();
                        this.airCurrent.pushing = true;
                        this.airCurrent.bounds = field.bounds();
                        this.airCurrent.maxDistance = this.computeVisualDistance(field);
                        this.airCurrent.segments.clear();
                    } else {
                        this.clearAirCurrent();
                    }
                } else {
                    this.clearAirCurrent();
                }
            }
        }
    }

    private float computeVisualDistance(WindTunnelFlowField field) {
        float baseLength = (float) field.length();
        float normalizedSpeed = (float) Mth.clamp(this.controllerAirspeed / (double) 64.0F, 0.0F, 1.0F);
        float speedBonus = Math.max(1.0F, baseLength * 0.45F) * normalizedSpeed;
        return baseLength + speedBonus;
    }

    private void clearAirCurrent() {
        this.cachedFlowField = null;
        this.cachedSearchBounds = new AABB(this.worldPosition);
        if (this.level != null && !this.level.isClientSide && this.trackedActive) {
            WindTunnelWindProvider.updateTracking(this, false);
            this.trackedActive = false;
        }

        this.airCurrent.direction = this.getFacing();
        this.airCurrent.pushing = true;
        this.airCurrent.maxDistance = 0.0F;
        this.airCurrent.bounds = new AABB(this.worldPosition);
        this.airCurrent.segments.clear();
    }

    private void markAirCurrentDirty() {
        this.airCurrentDirty = true;
    }
}
