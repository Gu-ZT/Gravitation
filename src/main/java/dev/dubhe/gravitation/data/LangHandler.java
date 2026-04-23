package dev.dubhe.gravitation.data;

import dev.anvilcraft.lib.v2.config.ConfigData;
import dev.anvilcraft.lib.v2.registrum.providers.RegistrumLangProvider;
import dev.dubhe.gravitation.Gravitation;
import dev.dubhe.gravitation.GravitationConfig;
import net.minecraft.Util;

public class LangHandler {
    public static void init(RegistrumLangProvider provider) {
        provider.add("gravitation.simulated_section.gravitation", "Gravitation");
        provider.add(Util.makeDescriptionId("scroll_option", Gravitation.location("max_mass")), "Max Mass");
        ConfigData.readConfigClass(provider, GravitationConfig.class);
    }
}
