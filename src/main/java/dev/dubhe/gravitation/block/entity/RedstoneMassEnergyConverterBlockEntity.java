package dev.dubhe.gravitation.block.entity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.dubhe.gravitation.Gravitation;
import dev.dubhe.gravitation.block.RedstoneMassEnergyConverterBlock;
import dev.dubhe.gravitation.init.ModBlocks;
import dev.dubhe.gravitation.mixin.LevelPlotAccessor;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import javax.annotation.Nullable;

import static dev.dubhe.gravitation.Gravitation.REGISTRUM;

public class RedstoneMassEnergyConverterBlockEntity extends SmartBlockEntity {
    protected @Nullable ScrollValueBehaviour maxConverterValue;
    private static final MutableComponent SCROLL_OPTION_TITLE = REGISTRUM.addLang(
        "scroll_option",
        Gravitation.location("max_mass"),
        "Max Mass"
    );
    private static final String VALUE_FORMAT = "%s kpg";
    private double oldMaxConverterValue = 0.25d;

    public RedstoneMassEnergyConverterBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState state
    ) {
        super(type, pos, state);
    }

    public final double getMaxConverterValue() {
        if (this.maxConverterValue == null) return 1.0d;
        return this.maxConverterValue.getValue();
    }

    public final int getMinConfigMass() {
        return Gravitation.CONFIG.getRedstoneMassEnergyConverterMinMass();
    }

    public final int getMaxConfigMass() {
        return Gravitation.CONFIG.getRedstoneMassEnergyConverterMaxMass();
    }

    public final int getConfiguredMaxMass() {
        return (int) this.getMaxConverterValue();
    }

    public final int setConfiguredMaxMass(int value) {
        int clamped = Math.clamp(value, this.getMinConfigMass(), this.getMaxConfigMass());
        if (this.maxConverterValue == null) return clamped;
        this.maxConverterValue.setValue(clamped);
        return this.maxConverterValue.getValue();
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.updateMass();
        this.oldMaxConverterValue = this.getMaxConverterValue();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.maxConverterValue = new ScrollValueBehaviour(
            SCROLL_OPTION_TITLE,
            this,
            new MassEnergyConverterValueBoxTransform()
        )
            .withFormatter(VALUE_FORMAT::formatted)
            .between(Gravitation.CONFIG.getRedstoneMassEnergyConverterMinMass(), Gravitation.CONFIG.getRedstoneMassEnergyConverterMaxMass())
            .withCallback(value -> {
                this.updateMass();
                this.oldMaxConverterValue = this.getMaxConverterValue();
            });
        this.maxConverterValue.value = 1;
        behaviours.add(this.maxConverterValue);
    }

    private static class MassEnergyConverterValueBoxTransform extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8f, 16);
        }
    }

    public double getMass() {
        BlockState blockState = this.getBlockState();
        if (!blockState.is(ModBlocks.REDSTONE_QUALITY_ENERGY_CONVERTER)) return 1.0d;
        Integer power = blockState.getValue(RedstoneMassEnergyConverterBlock.POWER);
        double maxConverterValue = this.maxConverterValue == null ? 1.0D : this.getMaxConverterValue();
        return Math.clamp(maxConverterValue * ((power + 1) / 16.0d), 0.25d, maxConverterValue);
    }

    public double getOldMass() {
        BlockState blockState = this.getBlockState();
        if (!blockState.is(ModBlocks.REDSTONE_QUALITY_ENERGY_CONVERTER)) return 1.0d;
        Integer power = blockState.getValue(RedstoneMassEnergyConverterBlock.POWER);
        double maxConverterValue = this.maxConverterValue == null ? 1.0D : this.oldMaxConverterValue;
        return Math.clamp(maxConverterValue * ((power + 1) / 16.0d), 0.25d, maxConverterValue);
    }

    @SuppressWarnings("UnstableApiUsage")
    public void updateMass() {
        final SubLevel subLevel = Sable.HELPER.getContaining(this.level, this.getBlockPos());
        if (!(subLevel instanceof final ServerSubLevel serverSubLevel)) {
            return;
        }
        BlockPos globalBlockPos = this.getBlockPos();
        BlockState oldState = this.getBlockState();
        BlockState newState = this.getBlockState();
        final Vec3 oldInertia = oldState.isAir() ? null : PhysicsBlockPropertyHelper.getInertia(this.level, globalBlockPos, oldState);
        final Vec3 inertia = newState.isAir() ? null : PhysicsBlockPropertyHelper.getInertia(this.level, globalBlockPos, newState);
        SubLevelPhysicsSystem subLevelPhysicsSystem = ((ServerSubLevelContainer) ((LevelPlotAccessor) serverSubLevel.getPlot()).getContainer()).physicsSystem();
        double oldMass = this.getOldMass();
        double mass = this.getMass();
        if (mass != oldMass || (newState != oldState && (oldMass != 0.0 || mass != 0.0)) || oldInertia != inertia) {
            final Level level = subLevel.getLevel();
            final MassTracker massTracker = serverSubLevel.getSelfMassTracker();

            if (mass != 0.0) massTracker.addBlockMass(level, newState, globalBlockPos, mass, inertia);
            if (oldMass != 0.0) massTracker.addBlockMass(level, oldState, globalBlockPos, -oldMass, oldInertia);

            if (!subLevel.isRemoved() && massTracker.isInvalid()) {
                serverSubLevel.getPlot().destroyAllBlocks();
                serverSubLevel.markRemoved();
                return;
            }

            serverSubLevel.updateMergedMassData((float) subLevelPhysicsSystem.getPartialPhysicsTick());

            subLevelPhysicsSystem.getPipeline().onStatsChanged(serverSubLevel);
        }
    }
}
