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

import javax.annotation.Nullable;

@Getter
public class WindTunnelMountMenu extends AbstractContainerMenu {
    private final BlockPos mountPos;
    public WindTunnelMountMenu(int containerId, BlockPos mountPos) {
        super(ModMenus.WIND_TUNNEL_MOUNT.get(), containerId);
        this.mountPos = mountPos;
    }

    public WindTunnelMountMenu(
        @Nullable MenuType<WindTunnelMountMenu> tMenuType,
        int containerId,
        @Nullable Inventory inventory,
        @Nullable RegistryFriendlyByteBuf extraData
    ) {
        super(tMenuType, containerId);
        if (extraData != null) {
            this.mountPos = extraData.readBlockPos();
        } else {
            this.mountPos = BlockPos.ZERO;
        }
    }

    public boolean stillValid(Player player) {
        return true;
    }

    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
