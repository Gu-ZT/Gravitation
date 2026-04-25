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
        provider.add("block.gravitation.wind_tunnel_mount_interface.selected", "Mount interface selected");
        provider.add("block.gravitation.wind_tunnel_mount_interface.not_on_aircraft", "Mount interface must be placed on an aircraft");
        provider.add("block.gravitation.wind_tunnel_mount.locked", "Locked");
        provider.add("block.gravitation.wind_tunnel_mount.unlocked", "Unlocked");
        provider.add("block.gravitation.wind_tunnel_mount.flow_direction", "Flow Direction");
        provider.add("block.gravitation.wind_tunnel_mount.angle_of_attack", "Angle of Attack");
        provider.add("block.gravitation.wind_tunnel_mount.sideslip_angle", "Sideslip Angle");
        provider.add("block.gravitation.wind_tunnel_mount.offset_x", "Offset X");
        provider.add("block.gravitation.wind_tunnel_mount.offset_y", "Offset Y");
        provider.add("block.gravitation.wind_tunnel_mount.offset_z", "Offset Z");
        provider.add("block.gravitation.wind_tunnel_mount.clear_binding", "Clear Binding");
        provider.add("block.gravitation.wind_tunnel_mount.measurement", "Measurement");
        provider.add("block.gravitation.wind_tunnel_mount.bound", "Mount binding updated");
        provider.add("block.gravitation.wind_tunnel_mount.binding_cleared", "Mount binding cleared");
        provider.add("block.gravitation.wind_tunnel_mount.selection_wrong_dimension", "Selected interface is in another dimension");
        provider.add("block.gravitation.wind_tunnel_mount.selection_invalid", "Selected block is no longer a mount interface");
        provider.add("block.gravitation.wind_tunnel_mount.selection_not_aircraft", "Selected interface is not on an aircraft");
    }
}
