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
public class WindTunnelControllerMenu extends AbstractContainerMenu {
    private final BlockPos controllerPos;

    public WindTunnelControllerMenu(int containerId, BlockPos controllerPos) {
        super(ModMenus.WIND_TUNNEL_CONTROLLER.get(), containerId);
        this.controllerPos = controllerPos;
    }

    public WindTunnelControllerMenu(
        MenuType<WindTunnelControllerMenu> tMenuType,
        int containerId,
        Inventory inventory,
        RegistryFriendlyByteBuf extraData
    ) {
        super(tMenuType, containerId);
        this.controllerPos = extraData.readBlockPos();
    }

    public boolean stillValid(Player player) {
        return player.level().isLoaded(this.controllerPos) && player.distanceToSqr(
            (double) this.controllerPos.getX() + (double) 0.5F,
            (double) this.controllerPos.getY() + (double) 0.5F,
            (double) this.controllerPos.getZ() + (double) 0.5F
        ) <= (double) 64.0F;
    }

    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
