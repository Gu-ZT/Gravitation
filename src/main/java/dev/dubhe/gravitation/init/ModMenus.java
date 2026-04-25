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
            WindTunnelControllerMenu::new,
            () -> WindTunnelControllerScreen::new
        )
        .register();
    public static final MenuEntry<WindTunnelMountMenu> WIND_TUNNEL_MOUNT = REGISTRUM
        .menu(
            "wind_tunnel_mount",
            WindTunnelMountMenu::new,
            () -> WindTunnelMountScreen::new
        )
        .register();

    public static void register() {
    }
}
