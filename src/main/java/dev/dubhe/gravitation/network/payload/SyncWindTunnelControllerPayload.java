package dev.dubhe.gravitation.network.payload;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.dubhe.gravitation.block.WindTunnelControllerBlock;
import dev.dubhe.gravitation.block.entity.WindTunnelControllerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncWindTunnelControllerPayload(BlockPos pos, int targetLength, int targetAirspeed, boolean enabled)
    implements IClientboundPacket {
    public static final Type<SyncWindTunnelControllerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
        "windtunnel",
        "sync_controller"
    ));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncWindTunnelControllerPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        SyncWindTunnelControllerPayload::pos,
        ByteBufCodecs.VAR_INT,
        SyncWindTunnelControllerPayload::targetLength,
        ByteBufCodecs.VAR_INT,
        SyncWindTunnelControllerPayload::targetAirspeed,
        ByteBufCodecs.BOOL,
        SyncWindTunnelControllerPayload::enabled,
        SyncWindTunnelControllerPayload::new
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncWindTunnelControllerPayload payload, IPayloadContext context) {
    }

    @Override
    public void handleOnClient(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            BlockEntity var4 = minecraft.level.getBlockEntity(this.pos());
            if (var4 instanceof WindTunnelControllerBlockEntity controller) {
                controller.applySettings(this.targetLength(), this.targetAirspeed());
                BlockState state = minecraft.level.getBlockState(this.pos());
                if (state.getBlock() instanceof WindTunnelControllerBlock && state.getValue(WindTunnelControllerBlock.ENABLED) != this.enabled()) {
                    minecraft.level.setBlock(this.pos(), state.setValue(WindTunnelControllerBlock.ENABLED, this.enabled()), 2);
                }
            }
        }
    }
}
