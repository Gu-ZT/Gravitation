package dev.dubhe.gravitation.client;

import dev.dubhe.gravitation.Gravitation;
import dev.dubhe.gravitation.client.init.ModPartialModels;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = Gravitation.MOD_ID, dist = Dist.CLIENT)
public class GravitationClient {
    public GravitationClient() {
        ModPartialModels.init();
    }
}
