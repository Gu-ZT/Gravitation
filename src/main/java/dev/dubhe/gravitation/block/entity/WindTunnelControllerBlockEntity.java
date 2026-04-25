package dev.dubhe.gravitation.block.entity;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.dubhe.gravitation.windtunnel.WindTunnelNetwork;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

@Getter
public class WindTunnelControllerBlockEntity extends SmartBlockEntity {
    private static final String TARGET_LENGTH_KEY = "TargetLength";
    private static final String TARGET_AIRSPEED_KEY = "TargetAirspeed";
    private static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 64;
    private static final int MIN_AIRSPEED = 0;
    public static final int MAX_AIRSPEED = 64;
    public static final int DEFAULT_LENGTH = 16;
    public static final int DEFAULT_AIRSPEED = 12;
    private int targetLength = DEFAULT_LENGTH;
    private int targetAirspeed = DEFAULT_AIRSPEED;

    public WindTunnelControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public boolean applySettings(int targetLength, int targetAirspeed) {
        return this.applySettings(targetLength, targetAirspeed, true);
    }

    public boolean applySettingsSilently(int targetLength, int targetAirspeed) {
        return this.applySettings(targetLength, targetAirspeed, false);
    }

    private boolean applySettings(int targetLength, int targetAirspeed, boolean refreshNetwork) {
        int clampedLength = Mth.clamp(targetLength, MIN_LENGTH, MAX_LENGTH);
        int clampedAirspeed = Mth.clamp(targetAirspeed, MIN_AIRSPEED, MAX_AIRSPEED);
        if (this.targetLength == clampedLength && this.targetAirspeed == clampedAirspeed) {
            return false;
        } else {
            this.targetLength = clampedLength;
            this.targetAirspeed = clampedAirspeed;
            this.notifySettingsChanged(refreshNetwork);
            return true;
        }
    }

    public void initialize() {
        super.initialize();
        if (this.level != null && !this.level.isClientSide) {
            WindTunnelNetwork.refreshFromController(this.level, this.worldPosition);
        }

    }

    public void remove() {
        if (this.level != null && !this.level.isClientSide) {
            WindTunnelNetwork.refreshAfterControllerRemoved(this.level, this.worldPosition);
        }

        super.remove();
    }

    private void notifySettingsChanged(boolean refreshNetwork) {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.sendData();
            if (refreshNetwork) {
                WindTunnelNetwork.refreshFromController(this.level, this.worldPosition);
            }
        }

    }

    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt(TARGET_LENGTH_KEY, this.targetLength);
        tag.putInt(TARGET_AIRSPEED_KEY, this.targetAirspeed);
    }

    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains(TARGET_LENGTH_KEY)) {
            this.targetLength = Mth.clamp(tag.getInt(TARGET_LENGTH_KEY), MIN_LENGTH, MAX_LENGTH);
        }

        if (tag.contains(TARGET_AIRSPEED_KEY)) {
            this.targetAirspeed = Mth.clamp(tag.getInt(TARGET_AIRSPEED_KEY), MIN_AIRSPEED, MAX_AIRSPEED);
        }
    }
}
