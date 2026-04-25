package dev.dubhe.gravitation.windtunnel;

public record WindTunnelMountMeasurement(
    double lift,
    double drag,
    double sideForce,
    double pitchMoment,
    double rollMoment,
    double yawMoment
) {
    public static final WindTunnelMountMeasurement EMPTY = new WindTunnelMountMeasurement(
        0.0F,
        0.0F,
        0.0F,
        0.0F,
        0.0F,
        0.0F
    );
    private static final double EPSILON = 1.0E-4;

    public boolean nearlyEquals(WindTunnelMountMeasurement other) {
        return Math.abs(this.lift - other.lift) <= EPSILON
               && Math.abs(this.drag - other.drag) <= EPSILON
               && Math.abs(this.sideForce - other.sideForce) <= EPSILON
               && Math.abs(this.pitchMoment - other.pitchMoment) <= EPSILON
               && Math.abs(this.rollMoment - other.rollMoment) <= EPSILON
               && Math.abs(this.yawMoment - other.yawMoment) <= EPSILON;
    }
}
