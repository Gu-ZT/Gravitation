package dev.dubhe.gravitation.windtunnel;

import dev.dubhe.gravitation.block.WindTunnelBlock;
import dev.dubhe.gravitation.block.WindTunnelControllerBlock;
import dev.dubhe.gravitation.block.entity.WindTunnelBlockEntity;
import dev.dubhe.gravitation.block.entity.WindTunnelControllerBlockEntity;
import dev.dubhe.gravitation.network.payload.SyncWindTunnelControllerPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class WindTunnelNetwork {
    private WindTunnelNetwork() {
    }

    public static void refreshFromController(Level level, BlockPos controllerPos) {
        refreshNetwork(level, controllerPos);
    }

    public static void refreshAfterControllerRemoved(Level level, BlockPos controllerPos) {
        refreshAdjacentNetworks(level, controllerPos);
    }

    public static void refreshConnectedTunnelCluster(Level level, BlockPos originTunnelPos) {
        refreshNetwork(level, originTunnelPos);
    }

    public static void refreshTunnel(Level level, BlockPos tunnelPos) {
        refreshNetwork(level, tunnelPos);
    }

    public static void refreshAdjacentNetworks(Level level, BlockPos originPos) {
        Set<BlockPos> refreshed = new HashSet<>();

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = originPos.relative(direction);
            if (!refreshed.contains(neighborPos)) {
                NetworkSnapshot network = scanNetwork(level, neighborPos);
                if (!network.isEmpty()) {
                    refreshed.addAll(network.controllers);
                    refreshed.addAll(network.tunnels);
                    applyNetworkState(level, network);
                }
            }
        }

    }

    private static void refreshNetwork(Level level, BlockPos originPos) {
        NetworkSnapshot network = scanNetwork(level, originPos);
        if (!network.isEmpty()) {
            applyNetworkState(level, network);
        }
    }

    private static void applyNetworkState(Level level, NetworkSnapshot network) {
        boolean controllerActive = false;
        boolean hasControllerLength = false;
        boolean hasControllerAirspeed = false;
        int targetLength = 16;
        double targetAirspeed = 12.0F;

        for (BlockPos controllerPos : network.controllers) {
            BlockState state = level.getBlockState(controllerPos);
            if (state.getBlock() instanceof WindTunnelControllerBlock) {
                if (state.getValue(WindTunnelControllerBlock.ENABLED) || state.getValue(WindTunnelControllerBlock.POWERED)) {
                    controllerActive = true;
                }

                BlockEntity blockEntity = level.getBlockEntity(controllerPos);
                if (blockEntity instanceof WindTunnelControllerBlockEntity controllerBlockEntity) {
                    int controllerLength = controllerBlockEntity.getTargetLength();
                    double controllerAirspeed = controllerBlockEntity.getTargetAirspeed();
                    syncController(level, controllerPos, controllerBlockEntity, state.getValue(WindTunnelControllerBlock.ENABLED));
                    targetLength = hasControllerLength ? Math.max(targetLength, controllerLength) : controllerLength;
                    targetAirspeed = hasControllerAirspeed ? Math.max(targetAirspeed, controllerAirspeed) : controllerAirspeed;
                    hasControllerLength = true;
                    hasControllerAirspeed = true;
                }
            }
        }

        for (BlockPos tunnelPos : network.tunnels) {
            applyTunnelState(level, tunnelPos, controllerActive, targetLength, targetAirspeed);
        }

    }

    private static void applyTunnelState(
        Level level,
        BlockPos tunnelPos,
        boolean controllerActive,
        int targetLength,
        double targetAirspeed
    ) {
        BlockState state = level.getBlockState(tunnelPos);
        if (state.getBlock() instanceof WindTunnelBlock) {
            BlockEntity blockEntity = level.getBlockEntity(tunnelPos);
            boolean settingsChanged = false;
            if (blockEntity instanceof WindTunnelBlockEntity windTunnelBlockEntity) {
                settingsChanged = !windTunnelBlockEntity.matchesControllerSettings(targetLength, targetAirspeed);
                if (settingsChanged) {
                    windTunnelBlockEntity.applyControllerSettings(targetLength, targetAirspeed);
                }
            }

            boolean shouldBePowered = level.hasNeighborSignal(tunnelPos) || controllerActive;
            if (state.getValue(WindTunnelBlock.POWERED) != shouldBePowered) {
                level.setBlock(tunnelPos, state.setValue(WindTunnelBlock.POWERED, shouldBePowered), 2);
                if (blockEntity instanceof WindTunnelBlockEntity windTunnelBlockEntity) {
                    windTunnelBlockEntity.onTunnelStateChanged();
                }
            } else if (settingsChanged && blockEntity instanceof WindTunnelBlockEntity windTunnelBlockEntity) {
                windTunnelBlockEntity.onTunnelStateChanged();
            }

        }
    }

    private static NetworkSnapshot scanNetwork(Level level, BlockPos originPos) {
        BlockState originState = level.getBlockState(originPos);
        if (!isNetworkBlock(originState)) {
            return NetworkSnapshot.EMPTY;
        } else {
            Set<BlockPos> controllers = new HashSet<>();
            Set<BlockPos> tunnels = new HashSet<>();
            Set<BlockPos> visited = new HashSet<>();
            ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
            frontier.add(originPos);
            visited.add(originPos);

            while (true) {
                BlockPos current;
                while (true) {
                    if (frontier.isEmpty()) {
                        return new NetworkSnapshot(controllers, tunnels);
                    }

                    current = frontier.removeFirst();
                    BlockState currentState = level.getBlockState(current);
                    if (currentState.getBlock() instanceof WindTunnelControllerBlock) {
                        controllers.add(current);
                        break;
                    }

                    if (currentState.getBlock() instanceof WindTunnelBlock) {
                        tunnels.add(current);
                        break;
                    }
                }

                for (Direction direction : Direction.values()) {
                    BlockPos neighborPos = current.relative(direction);
                    if (visited.add(neighborPos)) {
                        BlockState neighborState = level.getBlockState(neighborPos);
                        if (isNetworkBlock(neighborState)) {
                            frontier.addLast(neighborPos);
                        }
                    }
                }
            }
        }
    }

    private static boolean isNetworkBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof WindTunnelBlock || block instanceof WindTunnelControllerBlock;
    }

    private static void syncController(Level level, BlockPos controllerPos, WindTunnelControllerBlockEntity controller, boolean enabled) {
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersTrackingChunk(
                serverLevel,
                new ChunkPos(controllerPos),
                new SyncWindTunnelControllerPayload(controllerPos, controller.getTargetLength(), controller.getTargetAirspeed(), enabled)
            );
        }
    }

    private record NetworkSnapshot(Set<BlockPos> controllers, Set<BlockPos> tunnels) {
        private static final NetworkSnapshot EMPTY = new NetworkSnapshot(Set.of(), Set.of());

        private boolean isEmpty() {
            return this.controllers.isEmpty() && this.tunnels.isEmpty();
        }
    }
}
