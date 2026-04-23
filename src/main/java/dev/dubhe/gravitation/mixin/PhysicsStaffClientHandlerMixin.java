package dev.dubhe.gravitation.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.dubhe.gravitation.Gravitation;
import dev.dubhe.gravitation.init.ModItems;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffAction;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PhysicsStaffClientHandler.class)
public abstract class PhysicsStaffClientHandlerMixin {
    @Definition(id = "action", local = @Local(type = PhysicsStaffAction.class, argsOnly = true))
    @Definition(
        id = "START_DRAG",
        field = "Ldev/simulated_team/simulated/content/physics_staff/PhysicsStaffAction;START_DRAG:Ldev/simulated_team/simulated/content/physics_staff/PhysicsStaffAction;"
    )
    @Expression("action == START_DRAG")
    @ModifyExpressionValue(
        method = "onItemUsed",
        at = @At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 1)
    )
    public boolean onItemUsed(
        boolean original,
        @Local(name = "player") LocalPlayer player,
        @Local(name = "subLevel") SubLevel subLevel
    ) {
        if (!player.getMainHandItem().is(ModItems.PHYSICS_STAFF) && !player.getOffhandItem().is(ModItems.PHYSICS_STAFF)) return original;
        Vector3d size = subLevel.boundingBox().size();
        double sizeValue = size.x() * size.y() * size.z();
        if (sizeValue > Gravitation.CONFIG.physicsStaffAllowedMaxControlSize) return false;
        return original;
    }
}
