package dev.dubhe.gravitation.block.entity;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.AirFlowParticleData;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.dubhe.gravitation.block.FanConcentratorBlock;
import dev.dubhe.gravitation.windtunnel.WindTunnelFlowField;
import dev.dubhe.gravitation.windtunnel.WindTunnelNetwork;
import dev.dubhe.gravitation.windtunnel.WindTunnelWindProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import javax.annotation.Nullable;

public class FanConcentratorBlockEntity extends SyncedBlockEntity implements IAirCurrentSource, IHaveGoggleInformation {
    private static final float MIN_PARTICLE_SPAWN_CHANCE = 0.15F;
    private static final float MAX_PARTICLE_SPAWN_CHANCE = 0.85F;
    private static final int DUCT_RECHECK_INTERVAL = 10;
    private final AirCurrent airCurrent;
    private boolean airCurrentDirty = true;
    private Direction cachedFacing;
    private boolean cachedActive;
    private float cachedSourceSpeed;
    @Nullable
    private Direction cachedFlowDirection;
    @Nullable
    private IAirCurrentSource cachedSource;
    @Nullable
    private WindTunnelFlowField cachedFlowField;
    private AABB cachedSearchBounds;
    private boolean trackedActive;

    public FanConcentratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.cachedFacing = Direction.NORTH;
        this.cachedSourceSpeed = Float.NaN;
        this.airCurrent = new AirCurrent(this);
        this.cachedSearchBounds = new AABB(pos);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, FanConcentratorBlockEntity blockEntity) {
        blockEntity.tickAirFlowParticles();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FanConcentratorBlockEntity blockEntity) {
        blockEntity.tickServerDuctState();
    }

    public boolean isActive() {
        this.refreshAirCurrentIfNeeded();
        return this.cachedActive;
    }

    public Direction getFacing() {
        return this.getBlockState().getValue(FanConcentratorBlock.FACING);
    }

    public float getRenderedFanSpeed() {
        if (!this.isActive()) {
            return 0.0F;
        } else {
            float renderedSpeed = Math.abs(this.cachedSourceSpeed) * 4.0F;
            return Mth.clamp(renderedSpeed, 80.0F, 1280.0F);
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
        this.syncStateToClient();
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
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
        this.refreshAirCurrentIfNeeded();
        if (!this.cachedActive || this.cachedFlowDirection == null) {
            return 0.0F;
        }
        float magnitude = Math.abs(this.cachedSourceSpeed);
        return this.cachedFlowDirection == this.getFacing() ? magnitude : -magnitude;
    }

    public Direction getAirflowOriginSide() {
        return this.getFacing();
    }

    public Direction getAirFlowDirection() {
        this.refreshAirCurrentIfNeeded();
        return this.cachedActive ? this.cachedFlowDirection : null;
    }

    public float getMaxDistance() {
        this.refreshAirCurrentIfNeeded();
        return this.airCurrent.maxDistance;
    }

    public boolean isSourceRemoved() {
        return this.isRemoved();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        this.refreshAirCurrentIfNeeded();
        tooltip.add(
            Component.literal("    ")
                .append(Component.translatable("block.gravitation.fan_concentrator.goggles.title").withStyle(ChatFormatting.GRAY))
        );

        double length = 0.0F;
        double windSpeed = 0.0F;
        if (this.level != null) {
            Direction flowDirection = this.cachedFlowDirection != null ? this.cachedFlowDirection : this.getFacing();
            WindTunnelFlowField.DuctProbe probe = WindTunnelFlowField.probeSealedDuct(
                this.level,
                this.worldPosition,
                flowDirection,
                dev.dubhe.gravitation.Gravitation.CONFIG.windTunnel.maxRange
            );
            length = probe.length();
            if (this.cachedFlowField != null) {
                windSpeed = this.cachedFlowField.impulseMagnitude();
            }
        }

        tooltip.add(
            Component.literal("    ")
                .append(
                    Component.translatable("block.gravitation.fan_concentrator.goggles.length", String.format("%.2f", length))
                        .withStyle(ChatFormatting.AQUA)
                )
        );
        tooltip.add(
            Component.literal("    ")
                .append(
                    Component.translatable("block.gravitation.fan_concentrator.goggles.speed", String.format("%.2f", windSpeed))
                        .withStyle(ChatFormatting.AQUA)
                )
        );
        return true;
    }

    private void tickServerDuctState() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        long gameTime = this.level.getGameTime();
        int tickOffset = Math.floorMod(this.worldPosition.hashCode(), DUCT_RECHECK_INTERVAL);
        if (gameTime % DUCT_RECHECK_INTERVAL != tickOffset) {
            return;
        }

        this.refreshAirCurrentIfNeeded();
        Direction probeDirection = this.cachedFlowDirection != null ? this.cachedFlowDirection : this.getFacing();
        WindTunnelFlowField.DuctProbe probe = WindTunnelFlowField.probeSealedDuct(
            this.level,
            this.worldPosition,
            probeDirection,
            dev.dubhe.gravitation.Gravitation.CONFIG.windTunnel.maxRange
        );
        double currentLength = this.cachedFlowField == null ? 0.0F : this.cachedFlowField.length();
        if (Math.abs(probe.length() - currentLength) > 1.0E-4) {
            this.markAirCurrentDirty();
            this.refreshAirCurrentIfNeeded();
            this.syncStateToClient();
        }
    }

    private void syncStateToClient() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        this.setChanged();
        this.sendData();
    }

    private void tickAirFlowParticles() {
        if (this.level != null && this.level.isClientSide) {
            AirCurrent current = this.getAirCurrent();
            if (!(current.maxDistance <= 0.0F) && current.direction != null) {
                float normalizedSpeed = this.normalizedFanSpeed();
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
            IAirCurrentSource source = this.resolveAttachedSource(facing);
            Direction flowDirection = source == null ? null : this.resolveFlowDirection(source, facing);
            boolean active = flowDirection != null;
            float sourceSpeed = active ? source.getSpeed() : 0.0F;
            if (this.airCurrentDirty
                || this.cachedFacing != facing
                || this.cachedActive != active
                || this.cachedFlowDirection != flowDirection
                || this.cachedSource != source
                || Float.compare(this.cachedSourceSpeed, sourceSpeed) != 0) {
                this.cachedFacing = facing;
                this.cachedActive = active;
                this.cachedFlowDirection = flowDirection;
                this.cachedSource = source;
                this.cachedSourceSpeed = sourceSpeed;
                this.airCurrentDirty = false;
                if (active) {
                    WindTunnelFlowField field = WindTunnelFlowField.createFromFan(
                        this.level,
                        this.worldPosition,
                        flowDirection,
                        sourceSpeed
                    );
                    if (field != null && !(field.length() <= (double) 0.0F)) {
                        this.cachedFlowField = field;
                        this.cachedSearchBounds = field.bounds();
                        if (!this.level.isClientSide) {
                            WindTunnelWindProvider.updateTracking(this, true);
                            this.trackedActive = true;
                        }

                        this.airCurrent.direction = field.direction();
                        this.airCurrent.pushing = field.direction() == facing;
                        this.airCurrent.bounds = field.bounds();
                        this.airCurrent.maxDistance = (float) field.length();
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

    @Nullable
    private IAirCurrentSource resolveAttachedSource(Direction facing) {
        if (this.level == null) {
            return null;
        }
        BlockPos fanPos = this.worldPosition.relative(facing.getOpposite());
        if (!(this.level.getBlockEntity(fanPos) instanceof IAirCurrentSource source)) {
            return null;
        }
        if (source.isSourceRemoved()) {
            return null;
        }
        return source.getAirflowOriginSide() == facing ? source : null;
    }

    @Nullable
    private Direction resolveFlowDirection(IAirCurrentSource source, Direction facing) {
        Direction flowDirection = source.getAirFlowDirection();
        if (flowDirection == null || Math.abs(source.getSpeed()) <= 1.0E-3F) {
            return null;
        }
        return flowDirection == facing || flowDirection == facing.getOpposite() ? flowDirection : null;
    }

    private float normalizedFanSpeed() {
        if (Math.abs(this.cachedSourceSpeed) <= 0.0F) {
            return 0.0F;
        }
        float argmax = Math.max(1.0F, AllConfigs.server().kinetics.fanRotationArgmax.get());
        return Mth.clamp(Math.abs(this.cachedSourceSpeed) / argmax, 0.0F, 1.0F);
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
