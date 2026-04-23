package dev.dubhe.gravitation;

import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
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

    public int getRedstoneMassEnergyConverterMaxMass() {
        return Math.max(redstoneMassEnergyConverterMinMass, redstoneMassEnergyConverterMaxMass);
    }

    public int getRedstoneMassEnergyConverterMinMass() {
        return Math.min(redstoneMassEnergyConverterMinMass, redstoneMassEnergyConverterMaxMass);
    }
}
