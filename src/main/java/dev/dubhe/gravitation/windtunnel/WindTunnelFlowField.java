package dev.dubhe.gravitation.windtunnel;

import com.simibubi.create.AllTags.AllBlockTags;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import dev.dubhe.gravitation.Gravitation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

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
    private static final double[][] DEPTH_TEST_COORDINATES = new double[][]{
        {
            (double) 0.25F,
            (double) 0.25F
        },
        {
            (double) 0.25F,
            (double) 0.75F
        },
        {
            (double) 0.5F,
            (double) 0.5F
        },
        {
            (double) 0.75F,
            (double) 0.25F
        },
        {
            (double) 0.75F,
            (double) 0.75F
        }
    };

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
        double desiredLength = rpm / 4.0F;
        if (desiredLength <= 0.0F) {
            return null;
        }

        double airspeed = Math.log(rpm) / Math.log(2.0F);
        if (airspeed <= 0.0F) {
            return null;
        }

        int scanRange = Mth.clamp(Mth.ceil(desiredLength), 1, Gravitation.CONFIG.windTunnel.maxRange);
        WindTunnelFlowField field = create(level, origin, direction, scanRange, airspeed, Gravitation.CONFIG.windTunnel.maxAirspeed);
        if (field == null) {
            return null;
        }

        double limitedLength = Math.min(field.length(), desiredLength);
        if (limitedLength <= 0.0F) {
            return null;
        }

        return new WindTunnelFlowField(
            field.direction(),
            limitedLength,
            createBounds(origin, direction, limitedLength, Gravitation.CONFIG.windTunnel.crossSectionRadius),
            field.impulse(),
            field.normalizedImpulse(),
            field.impulseMagnitude(),
            field.nozzleCenter(),
            Math.max(1.0F, limitedLength),
            field.forceFalloff()
        );
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
        double openLength = findOpenLength(level, origin, direction, Math.max(1, configuredLength));
        if (openLength <= (double) 0.0F) {
            return null;
        } else {
            double clampedAirspeed = Mth.clamp(baseAirspeed, 0.0F, maxAirspeed);
            Vec3 impulse = Vec3.atLowerCornerOf(direction.getNormal()).scale(clampedAirspeed);
            Vec3 normalizedImpulse = clampedAirspeed <= 1.0E-8 ? Vec3.ZERO : Vec3.atLowerCornerOf(direction.getNormal());
            return new WindTunnelFlowField(
                direction,
                openLength,
                createBounds(origin, direction, openLength, Gravitation.CONFIG.windTunnel.crossSectionRadius),
                impulse,
                normalizedImpulse,
                clampedAirspeed,
                Vec3.atCenterOf(origin).add(impulse.scale(0.75F)),
                Math.max(1.0F, openLength),
                Gravitation.CONFIG.windTunnel.forceFalloff
            );
        }
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

    private static double findOpenLength(Level level, BlockPos origin, Direction direction, int maxRange) {
        for (int step = 0; step < maxRange; ++step) {
            BlockPos currentPos = origin.relative(direction, step + 1);
            if (!level.isLoaded(currentPos)) {
                return step;
            }

            BlockState currentState = level.getBlockState(currentPos);
            BlockState copycatState = CopycatBlock.getMaterial(level, currentPos);
            if (!shouldAlwaysPass(copycatState.isAir() ? currentState : copycatState)) {
                VoxelShape shape = currentState.getCollisionShape(level, currentPos);
                if (!shape.isEmpty()) {
                    if (shape == Shapes.block()) {
                        return step;
                    }

                    double shapeDepth = findMaxDepth(shape, direction);
                    if (shapeDepth != Double.POSITIVE_INFINITY) {
                        return Math.min((double) step + shapeDepth + (double) 0.03125F, maxRange);
                    }
                }
            }
        }

        return maxRange;
    }

    private static boolean shouldAlwaysPass(BlockState state) {
        return AllBlockTags.FAN_TRANSPARENT.matches(state);
    }

    private static double findMaxDepth(VoxelShape shape, Direction direction) {
        Direction.Axis axis = direction.getAxis();
        AxisDirection axisDirection = direction.getAxisDirection();
        double maxDepth = 0.0F;

        for (double[] coordinates : DEPTH_TEST_COORDINATES) {
            double depth;
            if (axisDirection == AxisDirection.POSITIVE) {
                double min = shape.min(axis, coordinates[0], coordinates[1]);
                if (min == Double.POSITIVE_INFINITY) {
                    return Double.POSITIVE_INFINITY;
                }

                depth = min;
            } else {
                double max = shape.max(axis, coordinates[0], coordinates[1]);
                if (max == Double.NEGATIVE_INFINITY) {
                    return Double.POSITIVE_INFINITY;
                }

                depth = (double) 1.0F - max;
            }

            if (depth > maxDepth) {
                maxDepth = depth;
            }
        }

        return maxDepth;
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
