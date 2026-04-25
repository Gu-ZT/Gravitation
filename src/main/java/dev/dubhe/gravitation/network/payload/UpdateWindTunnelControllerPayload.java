package dev.dubhe.gravitation.network.payload;

import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.dubhe.gravitation.block.WindTunnelControllerBlock;
import dev.dubhe.gravitation.block.entity.WindTunnelControllerBlockEntity;
import dev.dubhe.gravitation.windtunnel.WindTunnelNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public record UpdateWindTunnelControllerPayload(BlockPos pos, int targetLength, int targetAirspeed, boolean enabled)
    implements IServerboundPacket {
    public static final Type<UpdateWindTunnelControllerPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
        "windtunnel",
        "update_controller"
    ));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateWindTunnelControllerPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        UpdateWindTunnelControllerPayload::pos,
        ByteBufCodecs.VAR_INT,
        UpdateWindTunnelControllerPayload::targetLength,
        ByteBufCodecs.VAR_INT,
        UpdateWindTunnelControllerPayload::targetAirspeed,
        ByteBufCodecs.BOOL,
        UpdateWindTunnelControllerPayload::enabled,
        UpdateWindTunnelControllerPayload::new
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        BlockEntity var3 = player.level().getBlockEntity(this.pos());
        if (var3 instanceof WindTunnelControllerBlockEntity controller) {
            if (player.level().getBlockState(this.pos()).getBlock() instanceof WindTunnelControllerBlock) {
                boolean settingsChanged = controller.applySettingsSilently(this.targetLength(), this.targetAirspeed());
                Level level = player.level();
                BlockState state = level.getBlockState(this.pos());
                boolean enabledChanged = false;
                if (state.getValue(WindTunnelControllerBlock.ENABLED) != this.enabled()) {
                    level.setBlock(this.pos(), state.setValue(WindTunnelControllerBlock.ENABLED, this.enabled()), 2);
                    enabledChanged = true;
                }

                if (settingsChanged || enabledChanged) {
                    WindTunnelNetwork.refreshFromController(level, this.pos());
                }

            }
        }
    }
}
