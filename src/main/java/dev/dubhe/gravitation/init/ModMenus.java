package dev.dubhe.gravitation.init;

import dev.anvilcraft.lib.v2.registrum.util.entry.MenuEntry;
import dev.dubhe.gravitation.menu.WindTunnelControllerMenu;
import dev.dubhe.gravitation.client.screen.WindTunnelControllerScreen;
import dev.dubhe.gravitation.menu.WindTunnelMountMenu;
import dev.dubhe.gravitation.client.screen.WindTunnelMountScreen;

import static dev.dubhe.gravitation.Gravitation.REGISTRUM;

public class ModMenus {
    public static final MenuEntry<WindTunnelControllerMenu> WIND_TUNNEL_CONTROLLER = REGISTRUM
        .menu(
            "wind_tunnel_controller",
            (type, a, b, c) -> new WindTunnelControllerMenu(type, a, b, c),
            () -> WindTunnelControllerScreen::new
        )
        .register();
    public static final MenuEntry<WindTunnelMountMenu> WIND_TUNNEL_MOUNT = REGISTRUM
        .menu(
            "wind_tunnel_mount",
            (type, a, b, c) -> new WindTunnelMountMenu(type, a, b, c),
            () -> WindTunnelMountScreen::new
        )
        .register();

    public static void register() {
    }
}
