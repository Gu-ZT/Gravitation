package dev.dubhe.gravitation;

import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.CollapsibleObject;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;

@Config(name = Gravitation.MOD_ID)
public class GravitationConfig {
    @Comment("The minimum adjustable weight for the Redstone Mass Energy Converter")
    @BoundedDiscrete(min = 1, max = 1024)
    public int redstoneMassEnergyConverterMinMass = 1;
    @Comment("The maximum adjustable weight for the Redstone Mass Energy Converter")
    @BoundedDiscrete(min = 1, max = 1024)
    public int redstoneMassEnergyConverterMaxMass = 100;
    @Comment("The maximum size for physics staff allowed control")
    @BoundedDiscrete(min = 4096, max = 1073741824)
    public int physicsStaffAllowedMaxControlSize = 32768;
    @Comment("Whether physics staff can lock sub-levels")
    public boolean physicsStaffAllowLockSubLevel = false;
    @Comment("WindTunnel's Config")
    @CollapsibleObject
    public WindTunnelConfig windTunnel = new WindTunnelConfig();

    public int getRedstoneMassEnergyConverterMaxMass() {
        return Math.max(redstoneMassEnergyConverterMinMass, redstoneMassEnergyConverterMaxMass);
    }

    public int getRedstoneMassEnergyConverterMinMass() {
        return Math.min(redstoneMassEnergyConverterMinMass, redstoneMassEnergyConverterMaxMass);
    }

    public static class WindTunnelConfig {
        @Comment("Maximum number of open blocks scanned in front of a wind tunnel.")
        @BoundedDiscrete(min = 1, max = 64)
        public int maxRange = 16;
        @Comment("Base local air velocity injected by the tunnel, in blocks-per-second.")
        @BoundedDiscrete(min = 0.1, max = 128.0)
        public double baseAirspeed = 12.0;
        @Comment("How much the push weakens from the nozzle to the end of the stream.")
        @BoundedDiscrete(min = 0.0, max = 0.95)
        public double forceFalloff = 0.65;
        @Comment("How much the push weakens from the nozzle to the end of the stream.")
        @BoundedDiscrete(min = 0.1, max = 3.0)
        public double crossSectionRadius = 0.85;
        @Comment("Upper clamp for the injected local air velocity, in blocks-per-second.")
        @BoundedDiscrete(min = 0.1, max = 128.0)
        public double maxAirspeed = 64;
    }
}
