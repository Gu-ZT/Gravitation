package dev.dubhe.gravitation.init;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntry;
import dev.dubhe.gravitation.block.RedstoneMassEnergyConverterBlock;

import static dev.dubhe.gravitation.Gravitation.REGISTRUM;

public class ModBlocks {
    public static final BlockEntry<RedstoneMassEnergyConverterBlock> REDSTONE_QUALITY_ENERGY_CONVERTER = REGISTRUM
        .block("redstone_mass_energy_converter", RedstoneMassEnergyConverterBlock::new)
        .simpleItem()
        .register();

    public static void register() {
    }
}
