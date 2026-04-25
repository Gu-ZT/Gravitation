package dev.dubhe.gravitation;

import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.dubhe.gravitation.data.GravitationData;
import dev.dubhe.gravitation.init.ModBlockEntities;
import dev.dubhe.gravitation.init.ModBlocks;
import dev.dubhe.gravitation.init.ModCapabilities;
import dev.dubhe.gravitation.init.ModItems;
import dev.dubhe.gravitation.init.ModMenus;
import dev.dubhe.gravitation.windtunnel.WindTunnelMountService;
import dev.dubhe.gravitation.windtunnel.WindTunnelWindProvider;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

@Mod(Gravitation.MOD_ID)
public class Gravitation {
    public static final String MOD_ID = "gravitation";
    public static final GravitationRegistrum REGISTRUM = GravitationRegistrum.create(
        Gravitation.location(Gravitation.MOD_ID),
        Gravitation.MOD_ID
    );
    public static final GravitationConfig CONFIG = ConfigManager.register(Gravitation.MOD_ID, GravitationConfig::new);
    private static boolean windProviderRegistered;
    private static boolean mountHooksRegistered;

    public Gravitation(IEventBus modEventBus, ModContainer modContainer) {
        if (ModList.get().isLoaded("computercraft")) {
            modEventBus.addListener(ModCapabilities::register);
        }
        ModBlockEntities.register();
        ModBlocks.register();
        ModItems.register();
        ModMenus.register();
        GravitationData.init();

        if (!windProviderRegistered) {
            //noinspection UnstableApiUsage
            SubLevelHelper.registerWindProvider(WindTunnelWindProvider::getWindVelocityAt);
            windProviderRegistered = true;
        }

        if (!mountHooksRegistered) {
            SableEventPlatform.INSTANCE.onPhysicsTick(WindTunnelMountService::prePhysicsTick);
            SableEventPlatform.INSTANCE.onPostPhysicsTick(WindTunnelMountService::postPhysicsTick);
            mountHooksRegistered = true;
        }
    }

    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gravitation.MOD_ID, path);
    }
}
