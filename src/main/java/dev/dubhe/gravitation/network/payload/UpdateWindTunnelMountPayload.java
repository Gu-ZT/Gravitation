package dev.dubhe.gravitation.network.payload;

import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.dubhe.gravitation.block.entity.WindTunnelMountBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

public record UpdateWindTunnelMountPayload(
    BlockPos pos,
    boolean locked,
    Direction flowDirection,
    double angleOfAttack,
    double sideslipAngle,
    double offsetX,
    double offsetY,
    double offsetZ,
    boolean clearBinding
) implements IServerboundPacket {
    public static final Type<UpdateWindTunnelMountPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
        "windtunnel",
        "update_mount"
    ));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateWindTunnelMountPayload> STREAM_CODEC = StreamCodec.of(
        UpdateWindTunnelMountPayload::encode,
        UpdateWindTunnelMountPayload::decode
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static UpdateWindTunnelMountPayload decode(RegistryFriendlyByteBuf buffer) {
        return new UpdateWindTunnelMountPayload(
            BlockPos.STREAM_CODEC.decode(buffer),
            buffer.readBoolean(),
            Direction.from3DDataValue(buffer.readVarInt()),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readDouble(),
            buffer.readBoolean()
        );
    }

    private static void encode(RegistryFriendlyByteBuf buffer, UpdateWindTunnelMountPayload payload) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
        buffer.writeBoolean(payload.locked());
        buffer.writeVarInt(payload.flowDirection().get3DDataValue());
        buffer.writeDouble(payload.angleOfAttack());
        buffer.writeDouble(payload.sideslipAngle());
        buffer.writeDouble(payload.offsetX());
        buffer.writeDouble(payload.offsetY());
        buffer.writeDouble(payload.offsetZ());
        buffer.writeBoolean(payload.clearBinding());
    }

    @Override
    public void handleOnServer(Player player) {
        BlockEntity var3 = player.level().getBlockEntity(this.pos());
        if (var3 instanceof WindTunnelMountBlockEntity mount) {
            if (this.clearBinding()) {
                mount.clearBinding();
            } else {
                mount.applySettings(
                    this.locked(),
                    this.flowDirection(),
                    this.angleOfAttack(),
                    this.sideslipAngle(),
                    this.offsetX(),
                    this.offsetY(),
                    this.offsetZ()
                );
            }
        }
    }
}
