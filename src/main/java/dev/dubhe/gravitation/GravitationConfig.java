package dev.dubhe.gravitation;

import dev.anvilcraft.lib.v2.config.BoundedDiscrete;
import dev.anvilcraft.lib.v2.config.Comment;
import dev.anvilcraft.lib.v2.config.Config;

@Config(name = Gravitation.MOD_ID)
public class GravitationConfig {
    @Comment("The minimum adjustable weight for the Redstone Mass Energy Converter")
    @BoundedDiscrete(min = 1.0d, max = 1024.0d)
    public int redstoneMassEnergyConverterMinMass = 1;
    @Comment("The minimum adjustable weight for the Redstone Mass Energy Converter")
    @BoundedDiscrete(min = 1.0d, max = 1024.0d)
    public int redstoneMassEnergyConverterMaxMass = 100;

    public int getRedstoneMassEnergyConverterMaxMass() {
        return Math.max(redstoneMassEnergyConverterMinMass, redstoneMassEnergyConverterMaxMass);
    }

    public int getRedstoneMassEnergyConverterMinMass() {
        return Math.min(redstoneMassEnergyConverterMinMass, redstoneMassEnergyConverterMaxMass);
    }
}
