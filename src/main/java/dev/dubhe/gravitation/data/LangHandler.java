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
        provider.add("block.gravitation.fan_concentrator.goggles.title", "Duct Diagnostics");
        provider.add("block.gravitation.fan_concentrator.goggles.length", "Duct Length: %s");
        provider.add("block.gravitation.fan_concentrator.goggles.speed", "Wind Speed: %s");
        provider.add("gravitation.diagram.export", "Export Contraption Diagram To PNG");
        provider.add("gravitation.diagram.export.none_enabled", "Export contraption diagram not enabled in config");
        provider.add("gravitation.diagram.export.failure", "Contraption diagram PNG export failed: %s");
        provider.add("gravitation.diagram.export.success", "Exported %s contraption diagram image(s) to: %s");
    }
}
