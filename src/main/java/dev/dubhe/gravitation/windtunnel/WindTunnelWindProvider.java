package dev.dubhe.gravitation.windtunnel;

import dev.dubhe.gravitation.block.entity.WindTunnelBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WindTunnelWindProvider {
    private static final double QUERY_MARGIN = 0.25F;
    private static final Map<ResourceKey<Level>, DimensionTracking> ACTIVE_TUNNELS = new ConcurrentHashMap<>();

    private WindTunnelWindProvider() {
    }

    public static void updateTracking(WindTunnelBlockEntity blockEntity, boolean active) {
        if (blockEntity.getLevel() != null && !blockEntity.getLevel().isClientSide) {
            ResourceKey<Level> dimension = blockEntity.getLevel().dimension();
            if (!active) {
                removeTracking(dimension, blockEntity.getBlockPos().asLong());
                return;
            }
            WindTunnelFlowField field = blockEntity.getFlowField();
            if (field == null) {
                removeTracking(dimension, blockEntity.getBlockPos().asLong());
                return;
            }
            ACTIVE_TUNNELS.computeIfAbsent(dimension, (unused) -> new DimensionTracking())
                .put(TrackedTunnel.create(blockEntity.getBlockPos().immutable(), field));
        }
    }

    private static void removeTracking(ResourceKey<Level> dimension, long tunnelPos) {
        DimensionTracking tracking = ACTIVE_TUNNELS.get(dimension);
        if (tracking == null) {
            return;
        }
        tracking.remove(tunnelPos);
        if (tracking.isEmpty()) {
            ACTIVE_TUNNELS.remove(dimension, tracking);
        }
    }

    public static void unregister(WindTunnelBlockEntity blockEntity) {
        updateTracking(blockEntity, false);
    }

    public static @Nullable Vector3dc getWindVelocityAt(Vector3dc position, Level level) {
        DimensionTracking tracking = ACTIVE_TUNNELS.get(level.dimension());
        return tracking == null ? null : tracking.getWindVelocityAt(position.x(), position.y(), position.z());
    }

    private static final class DimensionTracking {
        private final Long2ObjectOpenHashMap<TrackedTunnel> tunnelsByPos = new Long2ObjectOpenHashMap<>();
        private volatile DimensionSnapshot snapshot;

        private DimensionTracking() {
            this.snapshot = DimensionSnapshot.EMPTY;
        }

        private synchronized void put(TrackedTunnel tunnel) {
            this.tunnelsByPos.put(tunnel.posLong(), tunnel);
            this.rebuildSnapshot();
        }

        private synchronized void remove(long tunnelPos) {
            if (this.tunnelsByPos.remove(tunnelPos) != null) {
                this.rebuildSnapshot();
            }
        }

        private boolean isEmpty() {
            return this.snapshot.isEmpty();
        }

        private @Nullable Vector3dc getWindVelocityAt(double sampleX, double sampleY, double sampleZ) {
            return this.snapshot.getWindVelocityAt(sampleX, sampleY, sampleZ);
        }

        private void rebuildSnapshot() {
            if (this.tunnelsByPos.isEmpty()) {
                this.snapshot = DimensionSnapshot.EMPTY;
            } else {
                Long2ObjectOpenHashMap<ArrayList<TrackedTunnel>> chunkBuilders = new Long2ObjectOpenHashMap<>();

                for (TrackedTunnel tunnel : this.tunnelsByPos.values()) {
                    for (long chunkKey : tunnel.chunkKeys()) {
                        ArrayList<TrackedTunnel> list = chunkBuilders.get(chunkKey);
                        if (list == null) {
                            list = new ArrayList<>();
                            chunkBuilders.put(chunkKey, list);
                        }

                        list.add(tunnel);
                    }
                }

                Long2ObjectOpenHashMap<TrackedTunnel[]> chunkIndex = new Long2ObjectOpenHashMap<>(chunkBuilders.size());

                for (long chunkKey : chunkBuilders.keySet()) {
                    ArrayList<TrackedTunnel> list = chunkBuilders.get(chunkKey);
                    chunkIndex.put(chunkKey, list.toArray(TrackedTunnel[]::new));
                }

                this.snapshot = new DimensionSnapshot(chunkIndex);
            }
        }
    }

    private record DimensionSnapshot(Long2ObjectOpenHashMap<TrackedTunnel[]> chunkIndex) {
        private static final DimensionSnapshot EMPTY = new DimensionSnapshot(new Long2ObjectOpenHashMap<>(0));

        private boolean isEmpty() {
            return this.chunkIndex.isEmpty();
        }

        private @Nullable Vector3dc getWindVelocityAt(double sampleX, double sampleY, double sampleZ) {
            long sampleChunk = ChunkPos.asLong(
                SectionPos.blockToSectionCoord(Mth.floor(sampleX)),
                SectionPos.blockToSectionCoord(Mth.floor(sampleZ))
            );
            TrackedTunnel[] candidateTunnels = this.chunkIndex.get(sampleChunk);
            if (candidateTunnels != null && candidateTunnels.length != 0) {
                Vector3d total = null;

                for (TrackedTunnel tunnel : candidateTunnels) {
                    if (tunnel.contains(sampleX, sampleY, sampleZ)) {
                        if (total == null) {
                            total = new Vector3d();
                        }

                        tunnel.accumulate(sampleX, sampleY, sampleZ, total);
                    }
                }

                return total;
            } else {
                return null;
            }
        }
    }

    private record TrackedTunnel(
        long posLong,
        AABB queryBounds,
        double nozzleCenterX,
        double nozzleCenterY,
        double nozzleCenterZ,
        double flowX,
        double flowY,
        double flowZ,
        double baseSpeed,
        double attenuationLength,
        double attenuationSlope,
        double minimumAttenuation,
        long[] chunkKeys
    ) {
        private static TrackedTunnel create(BlockPos pos, WindTunnelFlowField field) {
            AABB queryBounds = field.bounds().inflate(QUERY_MARGIN);
            double flowX = field.direction().getStepX();
            double flowY = field.direction().getStepY();
            double flowZ = field.direction().getStepZ();
            double baseSpeed = Math.abs(field.impulse().x) + Math.abs(field.impulse().y) + Math.abs(field.impulse().z);
            double forceFalloff = field.forceFalloff();
            double attenuationLength = Math.max(1.0F, field.length());
            return new TrackedTunnel(
                pos.asLong(),
                queryBounds,
                (double) pos.getX() + (double) 0.5F + field.impulse().x * (double) 0.75F,
                (double) pos.getY() + (double) 0.5F + field.impulse().y * (double) 0.75F,
                (double) pos.getZ() + (double) 0.5F + field.impulse().z * (double) 0.75F,
                flowX,
                flowY,
                flowZ,
                baseSpeed,
                attenuationLength,
                forceFalloff / attenuationLength,
                Math.max(0.15, (double) 1.0F - forceFalloff),
                computeChunkKeys(queryBounds)
            );
        }

        private static long[] computeChunkKeys(AABB bounds) {
            int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(bounds.minX));
            int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxX));
            int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.minZ));
            int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxZ));
            long[] chunkKeys = new long[(maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1)];
            int index = 0;

            for (int chunkX = minChunkX; chunkX <= maxChunkX; ++chunkX) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; ++chunkZ) {
                    chunkKeys[index++] = ChunkPos.asLong(chunkX, chunkZ);
                }
            }

            return chunkKeys;
        }

        private boolean contains(double sampleX, double sampleY, double sampleZ) {
            return this.queryBounds.contains(sampleX, sampleY, sampleZ);
        }

        private void accumulate(double sampleX, double sampleY, double sampleZ, Vector3d total) {
            double distanceAlongFlow = Math.max(
                0.0F,
                (sampleX - this.nozzleCenterX) * this.flowX
                + (sampleY - this.nozzleCenterY) * this.flowY
                + (sampleZ - this.nozzleCenterZ) * this.flowZ
            );
            double attenuation = distanceAlongFlow >= this.attenuationLength
                                 ? this.minimumAttenuation
                                 : Math.max(0.15, (double) 1.0F - distanceAlongFlow * this.attenuationSlope);
            double magnitude = this.baseSpeed * attenuation;
            if (!(magnitude <= (double) 0.0F)) {
                total.add(this.flowX * magnitude, this.flowY * magnitude, this.flowZ * magnitude);
            }
        }
    }
}
