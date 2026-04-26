package dev.dubhe.gravitation.client;

import dev.dubhe.gravitation.Gravitation;
import dev.dubhe.gravitation.client.init.ModPartialModels;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = Gravitation.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Gravitation.MOD_ID, value = Dist.CLIENT)
public class GravitationClient {
    public GravitationClient() {
        ModPartialModels.init();
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    }
}
