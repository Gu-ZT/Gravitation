package dev.dubhe.gravitation.windtunnel;

import com.simibubi.create.AllTags.AllBlockTags;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import dev.dubhe.gravitation.block.FanConcentratorBlock;
import dev.dubhe.gravitation.Gravitation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public record WindTunnelFlowField(
    Direction direction,
    double length,
    AABB bounds,
    Vec3 impulse,
    Vec3 normalizedImpulse,
    double impulseMagnitude,
    Vec3 nozzleCenter,
    double attenuationLength,
    double forceFalloff
) {
    public record DuctProbe(double length, boolean sealed) {
    }

    private static final Direction[] X_AXIS_SIDES = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH};
    private static final Direction[] Y_AXIS_SIDES = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final Direction[] Z_AXIS_SIDES = new Direction[]{Direction.UP, Direction.DOWN, Direction.WEST, Direction.EAST};

    public WindTunnelFlowField {
        attenuationLength = Math.max(1.0F, attenuationLength);
    }

    @Nullable
    public static WindTunnelFlowField create(Level level, BlockPos origin, Direction direction) {
        return create(
            level,
            origin,
            direction,
            Gravitation.CONFIG.windTunnel.maxRange,
            Gravitation.CONFIG.windTunnel.baseAirspeed,
            Gravitation.CONFIG.windTunnel.maxAirspeed
        );
    }

    @Nullable
    public static WindTunnelFlowField create(Level level, BlockPos origin, Direction direction, double baseAirspeed) {
        return create(
            level,
            origin,
            direction,
            Gravitation.CONFIG.windTunnel.maxRange,
            baseAirspeed,
            Gravitation.CONFIG.windTunnel.maxAirspeed
        );
    }

    @Nullable
    public static WindTunnelFlowField create(Level level, BlockPos origin, Direction direction, int configuredLength, double baseAirspeed) {
        return create(level, origin, direction, configuredLength, baseAirspeed, Math.max(Gravitation.CONFIG.windTunnel.maxAirspeed, 64.0F));
    }

    @Nullable
    public static WindTunnelFlowField createFromFan(
        Level level,
        BlockPos origin,
        Direction direction,
        float fanSpeed
    ) {
        double rpm = Math.abs(fanSpeed);
        double airspeed = Math.log(rpm) / Math.log(2.0F);
        if (airspeed <= 0.0F) {
            return null;
        }
        return create(level, origin, direction, Gravitation.CONFIG.windTunnel.maxRange, airspeed, Gravitation.CONFIG.windTunnel.maxAirspeed);
    }

    @Nullable
    private static WindTunnelFlowField create(
        Level level,
        BlockPos origin,
        Direction direction,
        int configuredLength,
        double baseAirspeed,
        double maxAirspeed
    ) {
        DuctProbe probe = probeSealedDuct(level, origin, direction, Math.max(1, configuredLength));
        double ductLength = probe.length();
        if (ductLength <= (double) 0.0F) {
            return null;
        } else {
            double clampedAirspeed = Mth.clamp(baseAirspeed, 0.0F, maxAirspeed);
            Vec3 impulse = Vec3.atLowerCornerOf(direction.getNormal()).scale(clampedAirspeed);
            Vec3 normalizedImpulse = clampedAirspeed <= 1.0E-8 ? Vec3.ZERO : Vec3.atLowerCornerOf(direction.getNormal());
            return new WindTunnelFlowField(
                direction,
                ductLength,
                createBounds(origin, direction, ductLength, Gravitation.CONFIG.windTunnel.crossSectionRadius),
                impulse,
                normalizedImpulse,
                clampedAirspeed,
                Vec3.atCenterOf(origin).add(impulse.scale(0.75F)),
                Math.max(1.0F, ductLength),
                Gravitation.CONFIG.windTunnel.forceFalloff
            );
        }
    }

    public static DuctProbe probeSealedDuct(Level level, BlockPos origin, Direction direction, int maxRange) {
        return findSealedDuctLength(level, origin, direction, Math.max(1, maxRange));
    }

    public double attenuationFor(Vec3 samplePoint, BlockPos origin) {
        double distanceAlongFlow = Math.max(0.0F, samplePoint.subtract(this.nozzleCenter).dot(this.normalizedImpulse));
        double progress = Mth.clamp(distanceAlongFlow / this.attenuationLength, 0.0F, 1.0F);
        return Math.max(0.15, (double) 1.0F - progress * this.forceFalloff);
    }

    public Vec3 airVelocityAt(Vec3 samplePoint, BlockPos origin) {
        if (!this.bounds.contains(samplePoint)) {
            return Vec3.ZERO;
        } else {
            double magnitude = this.impulseMagnitude * this.attenuationFor(samplePoint, origin);
            return magnitude <= (double) 0.0F ? Vec3.ZERO : this.normalizedImpulse.scale(magnitude);
        }
    }

    private static DuctProbe findSealedDuctLength(Level level, BlockPos origin, Direction direction, int maxRange) {
        Set<BlockPos> sectionOffsets = collectSectionOffsets(level, origin, direction);
        if (sectionOffsets.isEmpty()) {
            return new DuctProbe(0, false);
        }

        Direction[] sideDirections = getSideDirections(direction.getAxis());
        // Start sealing checks at the first block in front of the concentrator face.
        for (int distance = 1; distance <= maxRange; ++distance) {
            if (!isLayerPassableAndEdgeSealed(level, origin, direction, distance, sectionOffsets, sideDirections)) {
                return new DuctProbe(distance - 1, false);
            }
        }

        return new DuctProbe(maxRange, true);
    }

    private static Set<BlockPos> collectSectionOffsets(Level level, BlockPos origin, Direction direction) {
        if (!isSectionNozzle(level, origin, direction)) {
            return Set.of();
        }

        Direction[] sideDirections = getSideDirections(direction.getAxis());
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> sectionOffsets = new HashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(origin);
        visited.add(origin);

        while (!frontier.isEmpty()) {
            BlockPos current = frontier.removeFirst();
            sectionOffsets.add(current.subtract(origin));

            for (Direction side : sideDirections) {
                BlockPos next = current.relative(side);
                if (visited.add(next) && isSectionNozzle(level, next, direction)) {
                    frontier.addLast(next);
                }
            }
        }

        return sectionOffsets;
    }

    private static boolean isLayerPassableAndEdgeSealed(
        Level level,
        BlockPos origin,
        Direction direction,
        int distance,
        Set<BlockPos> sectionOffsets,
        Direction[] sideDirections
    ) {
        for (BlockPos offset : sectionOffsets) {
            BlockPos probePos = origin.offset(offset).relative(direction, distance);
            if (!level.isLoaded(probePos) || !isDuctPassable(level, probePos)) {
                return false;
            }
        }

        // For arbitrary cross-sections, only enforce sealing on perimeter edges.
        for (BlockPos offset : sectionOffsets) {
            BlockPos probePos = origin.offset(offset).relative(direction, distance);
            for (Direction side : sideDirections) {
                if (sectionOffsets.contains(offset.relative(side))) {
                    continue;
                }
                BlockPos wallPos = probePos.relative(side);
                if (!level.isLoaded(wallPos) || !isFullSealBlock(level, wallPos)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isSectionNozzle(Level level, BlockPos pos, Direction direction) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FanConcentratorBlock)) {
            return false;
        }
        return state.getValue(FanConcentratorBlock.FACING) == direction;
    }

    private static boolean isDuctPassable(Level level, BlockPos pos) {
        BlockState rawState = level.getBlockState(pos);
        if (rawState.getBlock() instanceof FanConcentratorBlock) {
            return true;
        }
        BlockState state = getEffectiveState(level, pos);
        if (state.isAir() || shouldAlwaysPass(state)) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty();
    }

    private static Direction[] getSideDirections(Direction.Axis axis) {
        return switch (axis) {
            case X -> X_AXIS_SIDES;
            case Y -> Y_AXIS_SIDES;
            case Z -> Z_AXIS_SIDES;
        };
    }

    private static boolean isFullSealBlock(Level level, BlockPos pos) {
        BlockState state = getEffectiveState(level, pos);
        // Strict wall check: only full collision blocks are valid pipe walls.
        return !state.isAir() && state.isCollisionShapeFullBlock(level, pos);
    }

    private static BlockState getEffectiveState(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState copycatState = CopycatBlock.getMaterial(level, pos);
        return copycatState.isAir() ? state : copycatState;
    }

    private static boolean shouldAlwaysPass(BlockState state) {
        return AllBlockTags.FAN_TRANSPARENT.matches(state);
    }


    private static AABB createBounds(BlockPos origin, Direction direction, double length, double radius) {
        AABB baseBox = new AABB(origin.relative(direction));
        Vec3 directionVec = Vec3.atLowerCornerOf(direction.getNormal());
        double factor = length - (double) 1.0F;
        Vec3 scale = directionVec.scale(factor);
        AABB flowBox = factor > (double) 0.0F ? baseBox.expandTowards(scale) : baseBox.contract(scale.x, scale.y, scale.z).move(scale);
        AABB var10000;
        switch (direction.getAxis()) {
            case X -> var10000 = flowBox.inflate(0.0F, radius, radius);
            case Y -> var10000 = flowBox.inflate(radius, 0.0F, radius);
            case Z -> var10000 = flowBox.inflate(radius, radius, 0.0F);
            default -> throw new MatchException(null, null);
        }

        return var10000;
    }
}
