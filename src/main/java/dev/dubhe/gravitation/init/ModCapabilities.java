package dev.dubhe.gravitation.init;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.dubhe.gravitation.compat.cc.RedstoneMassEnergyConverterPeripheral;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class ModCapabilities {
    private ModCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            PeripheralCapability.get(),
            ModBlockEntities.REDSTONE_QUALITY_ENERGY_CONVERTER.get(),
            (blockEntity, side) -> new RedstoneMassEnergyConverterPeripheral(blockEntity)
        );
    }
}

