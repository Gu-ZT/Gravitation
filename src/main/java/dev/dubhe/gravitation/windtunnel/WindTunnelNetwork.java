package dev.dubhe.gravitation.windtunnel;

import dev.dubhe.gravitation.block.FanConcentratorBlock;
import dev.dubhe.gravitation.block.entity.FanConcentratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
            if (refreshed.add(neighborPos)) {
                applyNetworkState(level, scanNetwork(level, neighborPos));
            }
        }

    }

    private static void refreshNetwork(Level level, BlockPos originPos) {
        applyNetworkState(level, scanNetwork(level, originPos));
    }

    private static void applyNetworkState(Level level, NetworkSnapshot network) {
        for (BlockPos tunnelPos : network.tunnels) {
            applyTunnelState(level, tunnelPos);
        }

    }

    private static void applyTunnelState(Level level, BlockPos tunnelPos) {
        BlockState state = level.getBlockState(tunnelPos);
        if (state.getBlock() instanceof FanConcentratorBlock) {
            BlockEntity blockEntity = level.getBlockEntity(tunnelPos);
            if (blockEntity instanceof FanConcentratorBlockEntity fanConcentratorBlockEntity) {
                fanConcentratorBlockEntity.onTunnelStateChanged();
            }
        }
    }

    private static NetworkSnapshot scanNetwork(Level level, BlockPos originPos) {
        BlockState originState = level.getBlockState(originPos);
        if (!isNetworkBlock(originState)) {
            return NetworkSnapshot.EMPTY;
        } else {
            Set<BlockPos> tunnels = new HashSet<>();
            Set<BlockPos> visited = new HashSet<>();
            ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
            frontier.add(originPos);
            visited.add(originPos);

            while (true) {
                BlockPos current;
                while (true) {
                    if (frontier.isEmpty()) {
                        return new NetworkSnapshot(tunnels);
                    }

                    current = frontier.removeFirst();
                    BlockState currentState = level.getBlockState(current);
                    if (currentState.getBlock() instanceof FanConcentratorBlock) {
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
        return block instanceof FanConcentratorBlock;
    }

    private record NetworkSnapshot(Set<BlockPos> tunnels) {
        private static final NetworkSnapshot EMPTY = new NetworkSnapshot(Set.of());

        private boolean isEmpty() {
            return this.tunnels.isEmpty();
        }
    }
}
