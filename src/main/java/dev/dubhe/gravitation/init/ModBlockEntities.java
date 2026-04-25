package dev.dubhe.gravitation.init;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntityEntry;
import dev.dubhe.gravitation.block.entity.RedstoneMassEnergyConverterBlockEntity;
import dev.dubhe.gravitation.client.renderer.RedstoneMassEnergyConverterBlockEntityRenderer;
import dev.dubhe.gravitation.block.entity.WindTunnelBlockEntity;
import dev.dubhe.gravitation.block.entity.WindTunnelMountBlockEntity;

import static dev.dubhe.gravitation.Gravitation.REGISTRUM;

public class ModBlockEntities {
    public static final BlockEntityEntry<RedstoneMassEnergyConverterBlockEntity> REDSTONE_QUALITY_ENERGY_CONVERTER = REGISTRUM
        .blockEntity("redstone_mass_energy_converter", RedstoneMassEnergyConverterBlockEntity::new)
        .validBlock(ModBlocks.REDSTONE_QUALITY_ENERGY_CONVERTER)
        .renderer(() -> RedstoneMassEnergyConverterBlockEntityRenderer::new)
        .register();

    public static final BlockEntityEntry<WindTunnelBlockEntity> FAN_CONCENTRATOR = REGISTRUM
        .blockEntity("fan_concentrator", WindTunnelBlockEntity::new)
        .validBlock(ModBlocks.FAN_CONCENTRATOR)
        .register();

    public static final BlockEntityEntry<WindTunnelMountBlockEntity> WIND_TUNNEL_MOUNT = REGISTRUM
        .blockEntity("wind_tunnel_mount", WindTunnelMountBlockEntity::new)
        .validBlock(ModBlocks.WIND_TUNNEL_MOUNT)
        .register();

    public static void register() {
    }
}
