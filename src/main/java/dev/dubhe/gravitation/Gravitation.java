package dev.dubhe.gravitation;

import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.dubhe.gravitation.data.GravitationData;
import dev.dubhe.gravitation.init.ModBlockEntities;
import dev.dubhe.gravitation.init.ModBlocks;
import dev.dubhe.gravitation.init.ModCapabilities;
import dev.dubhe.gravitation.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Gravitation.MOD_ID)
public class Gravitation {
    public static final String MOD_ID = "gravitation";
    public static final GravitationRegistrum REGISTRUM = GravitationRegistrum.create(
        Gravitation.location(Gravitation.MOD_ID),
        Gravitation.MOD_ID
    );
    public static final GravitationConfig CONFIG = ConfigManager.register(Gravitation.MOD_ID, GravitationConfig::new);

    public Gravitation(IEventBus modEventBus, ModContainer modContainer) {
        if (ModList.get().isLoaded("computercraft")) {
            modEventBus.addListener(ModCapabilities::register);
        }
        ModBlockEntities.register();
        ModBlocks.register();
        ModItems.register();
        GravitationData.init();
    }

    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gravitation.MOD_ID, path);
    }
}
