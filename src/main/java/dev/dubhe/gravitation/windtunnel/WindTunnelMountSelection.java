package dev.dubhe.gravitation.windtunnel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public final class WindTunnelMountSelection {
    private static final String ROOT_KEY = "windtunnel_mount_selection";
    private static final String DIMENSION_KEY = "Dimension";
    private static final String POS_KEY = "Pos";

    private WindTunnelMountSelection() {
    }

    public static void store(Player player, ResourceKey<Level> dimension, BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DIMENSION_KEY, dimension.location().toString());
        tag.putLong(POS_KEY, pos.asLong());
        player.getPersistentData().put(ROOT_KEY, tag);
    }

    @Nullable
    public static Selection get(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT_KEY, 10)) {
            return null;
        } else {
            CompoundTag tag = root.getCompound(ROOT_KEY);
            ResourceLocation location = ResourceLocation.tryParse(tag.getString(DIMENSION_KEY));
            return location != null && tag.contains(POS_KEY) ? new Selection(
                ResourceKey.create(Registries.DIMENSION, location),
                BlockPos.of(tag.getLong(POS_KEY))
            ) : null;
        }
    }

    public static void clear(Player player) {
        player.getPersistentData().remove(ROOT_KEY);
    }

    public static record Selection(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
