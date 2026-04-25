package dev.dubhe.gravitation.menu;

import dev.dubhe.gravitation.init.ModMenus;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

@Getter
public class WindTunnelMountMenu extends AbstractContainerMenu {
    private final BlockPos mountPos;
    public WindTunnelMountMenu(int containerId, BlockPos mountPos) {
        super(ModMenus.WIND_TUNNEL_MOUNT.get(), containerId);
        this.mountPos = mountPos;
    }

    public WindTunnelMountMenu(
        MenuType<WindTunnelMountMenu> tMenuType,
        int containerId,
        Inventory inventory,
        RegistryFriendlyByteBuf extraData
    ) {
        super(tMenuType, containerId);
        this.mountPos = extraData.readBlockPos();
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
