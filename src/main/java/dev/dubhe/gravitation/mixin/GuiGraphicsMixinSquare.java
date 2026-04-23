package dev.dubhe.gravitation.mixin;


import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.dubhe.gravitation.init.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GuiGraphics.class, priority = 1500)
public class GuiGraphicsMixinSquare {
    @TargetHandler(
        mixin = "dev.simulated_team.simulated.mixin.physics_staff.GuiGraphicsMixin",
        name = "simulated$renderPhysicsStaff"
    )
    @WrapOperation(
        method = "@MixinSquared:Handler",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/core/Holder;)Z"
        )
    )
    private boolean simulated$renderPhysicsStaff(ItemStack instance, Holder<Item> item, Operation<Boolean> original) {
        if (instance.is(ModItems.PHYSICS_STAFF)) return true;
        return original.call(instance, item);
    }
}
