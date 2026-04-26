package dev.dubhe.gravitation.compat.cc;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.dubhe.gravitation.block.entity.RedstoneMassEnergyConverterBlockEntity;

import java.util.Map;

public class RedstoneMassEnergyConverterPeripheral implements IPeripheral {
    private final RedstoneMassEnergyConverterBlockEntity blockEntity;

    public RedstoneMassEnergyConverterPeripheral(RedstoneMassEnergyConverterBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public String getType() {
        return "redstone_mass_energy_converter";
    }

    @LuaFunction(mainThread = true)
    public final int getMaxMass() {
        return this.blockEntity.getConfiguredMaxMass();
    }

    @LuaFunction(mainThread = true)
    public final int setMaxMass(int value) {
        return this.blockEntity.setConfiguredMaxMass(value);
    }

    @LuaFunction(mainThread = true)
    public final double getCurrentMass() {
        return this.blockEntity.getMass();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Integer> getRange() {
        return Map.of(
            "min", this.blockEntity.getMinConfigMass(),
            "max", this.blockEntity.getMaxConfigMass()
        );
    }

    @Override
    public boolean equals(IPeripheral other) {
        if (!(other instanceof RedstoneMassEnergyConverterPeripheral peripheral)) return false;
        return this.blockEntity == peripheral.blockEntity;
    }

    @Override
    public Object getTarget() {
        return this.blockEntity;
    }
}

