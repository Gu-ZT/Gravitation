package dev.dubhe.gravitation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dubhe.gravitation.init.ModItems;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffRenderHandler;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PhysicsStaffRenderHandler.class)
public abstract class PhysicsStaffRenderHandlerMixin {

    @WrapOperation(
        method = "renderSelectionBox",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/core/Holder;)Z")
    )
    private static boolean renderSelectionBox(ItemStack instance, Holder<Item> item, Operation<Boolean> original) {
        if (instance.is(ModItems.PHYSICS_STAFF)) return true;
        return original.call(instance, item);
    }
}
