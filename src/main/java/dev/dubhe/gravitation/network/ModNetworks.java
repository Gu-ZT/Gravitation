package dev.dubhe.gravitation.network;

import dev.anvilcraft.lib.v2.network.register.NetworkRegistrar;
import dev.dubhe.gravitation.Gravitation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Gravitation.MOD_ID)
public class ModNetworks {
    public static final String NETWORK_VERSION = "1";

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        NetworkRegistrar.register(registrar, Gravitation.MOD_ID);
    }
}
