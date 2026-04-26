package dev.dubhe.gravitation.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.dubhe.gravitation.client.init.ModPartialModels;
import dev.dubhe.gravitation.init.ModItems;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItemRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PhysicsStaffItemRenderer.class)
public class PhysicsStaffItemRendererMixin {
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Ldev/engine_room/flywheel/lib/model/baked/PartialModel;get()Lnet/minecraft/client/resources/model/BakedModel;"
        )
    )
    private BakedModel render(PartialModel instance, Operation<BakedModel> original, @Local(argsOnly = true) ItemStack item) {
        if (item.is(ModItems.PHYSICS_STAFF)) {
            if (instance == SimPartialModels.PHYSICS_STAFF_CORE) {
                return ModPartialModels.PHYSICS_STAFF_CORE.get();
            }
            if (instance == SimPartialModels.PHYSICS_STAFF_CORE_GLOW) {
                return ModPartialModels.PHYSICS_STAFF_CORE_GLOW.get();
            }
            if (instance == SimPartialModels.PHYSICS_STAFF_RING) {
                return ModPartialModels.PHYSICS_STAFF_RING.get();
            }
            if (instance == SimPartialModels.PHYSICS_STAFF_SIGMA) {
                return ModPartialModels.PHYSICS_STAFF_SIGMA.get();
            }
            if (instance == SimPartialModels.PHYSICS_STAFF_INNER_CUBE) {
                return ModPartialModels.PHYSICS_STAFF_INNER_CUBE.get();
            }
            if (instance == SimPartialModels.PHYSICS_STAFF_OUTER_CUBE) {
                return ModPartialModels.PHYSICS_STAFF_OUTER_CUBE.get();
            }
        }
        return original.call(instance);
    }
}
