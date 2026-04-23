package dev.dubhe.gravitation.client.init;

import dev.dubhe.gravitation.Gravitation;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

@SuppressWarnings("SameParameterValue")
public class ModPartialModels {
    public static final PartialModel
        REDSTONE_MASS_ENERGY_CONVERTER_INNER = block("redstone_mass_energy_converter_inner"),
        PHYSICS_STAFF_CORE_GLOW = item("physics_staff/core_glow"),
        PHYSICS_STAFF_CORE = item("physics_staff/core"),
        PHYSICS_STAFF_RING = item("physics_staff/ring"),
        PHYSICS_STAFF_SIGMA = item("physics_staff/sigma"),
        PHYSICS_STAFF_INNER_CUBE = item("physics_staff/inner_cube"),
        PHYSICS_STAFF_OUTER_CUBE = item("physics_staff/outer_cube");


    private static PartialModel block(final String path) {
        return PartialModel.of(Gravitation.location(path).withPrefix("block/"));
    }

    private static PartialModel item(final String path) {
        return PartialModel.of(Gravitation.location(path).withPrefix("item/"));
    }

    public static void init() {
    }
}
