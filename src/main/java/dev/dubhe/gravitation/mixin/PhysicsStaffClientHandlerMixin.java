package dev.dubhe.gravitation.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.dubhe.gravitation.Gravitation;
import dev.dubhe.gravitation.init.ModItems;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffAction;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(PhysicsStaffClientHandler.class)
public abstract class PhysicsStaffClientHandlerMixin {
    @Shadow
    @Nullable
    private PhysicsStaffClientHandler.ClientDragSession dragSession;

    @Invoker("stopDragging")
    public abstract void gravitation$invokeStopDragging();

    @Inject(method = "onItemUsed", at = @At("HEAD"), cancellable = true)
    private void gravitation$denyLockWhenDisabled(PhysicsStaffAction action, CallbackInfo ci) {
        if (action != PhysicsStaffAction.LOCK || Gravitation.CONFIG.physicsStaffAllowLockSubLevel) {
            return;
        }

        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!player.getMainHandItem().is(ModItems.PHYSICS_STAFF) && !player.getOffhandItem().is(ModItems.PHYSICS_STAFF)) {
            return;
        }

        player.stopUsingItem();
        ci.cancel();
    }

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
    public boolean onItemUsedStartDrag(
        boolean original,
        @Local(name = "player") LocalPlayer player,
        @Local(name = "subLevel") SubLevel subLevel
    ) {
        return this.gravitation$onItemUsedUtil(original, player, subLevel);
    }

    @Definition(id = "action", local = @Local(type = PhysicsStaffAction.class, argsOnly = true))
    @Definition(
        id = "LOCK",
        field = "Ldev/simulated_team/simulated/content/physics_staff/PhysicsStaffAction;LOCK:Ldev/simulated_team/simulated/content/physics_staff/PhysicsStaffAction;"
    )
    @Expression("action == LOCK")
    @ModifyExpressionValue(
        method = "onItemUsed",
        at = @At(value = "MIXINEXTRAS:EXPRESSION", ordinal = 1)
    )
    public boolean onItemUsedLock(
        boolean original,
        @Local(name = "player") LocalPlayer player,
        @Local(name = "subLevel") SubLevel subLevel
    ) {
        return this.gravitation$onItemUsedUtil(original, player, subLevel);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void gravitation$interruptWhenPlayerTouchesSubLevel(CallbackInfo ci) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || this.dragSession == null) {
            return;
        }
        if (!player.getMainHandItem().is(ModItems.PHYSICS_STAFF) && !player.getOffhandItem().is(ModItems.PHYSICS_STAFF)) {
            return;
        }

        if (this.gravitation$isPlayerCollidingWithAnySubLevel(player)
            || this.gravitation$isPlayerStandingOnAnySubLevel(player)) {
            this.gravitation$invokeStopDragging();
            player.stopUsingItem();
        }
    }

    @Unique
    private boolean gravitation$onItemUsedUtil(boolean original, LocalPlayer player, SubLevel subLevel) {
        if (!player.getMainHandItem().is(ModItems.PHYSICS_STAFF) && !player.getOffhandItem().is(ModItems.PHYSICS_STAFF)) return original;
        if (this.gravitation$isPlayerCollidingWithAnySubLevel(player)
            || this.gravitation$isPlayerStandingOnAnySubLevel(player)) {
            player.stopUsingItem();
            return false;
        }
        Vector3d size = subLevel.boundingBox().size();
        double sizeValue = size.x() * size.y() * size.z();
        if (sizeValue > Gravitation.CONFIG.physicsStaffAllowedMaxControlSize) return false;
        return original;
    }

    @Unique
    @SuppressWarnings("UnstableApiUsage")
    private boolean gravitation$isPlayerCollidingWithAnySubLevel(LocalPlayer player) {
        if (!(player instanceof EntityMovementExtension movementExtension)) {
            return false;
        }

        SubLevelEntityCollision.CollisionInfo collisionInfo = movementExtension.sable$getCollisionInfo();
        return collisionInfo != null
               && ((collisionInfo.firstCollisions != null && !collisionInfo.firstCollisions.isEmpty())
                   || collisionInfo.subLevelHorizontalCollision);
    }

    @Unique
    private boolean gravitation$isPlayerStandingOnAnySubLevel(LocalPlayer player) {
        BlockPos onPos = player.getOnPos();
        return Sable.HELPER.getContaining(player.level(), onPos) != null
               || Sable.HELPER.getTrackingOrVehicleSubLevel(player) != null;
    }
}
