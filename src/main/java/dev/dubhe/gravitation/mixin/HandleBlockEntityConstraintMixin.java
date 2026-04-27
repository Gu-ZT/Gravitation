package dev.dubhe.gravitation.mixin;

import dev.dubhe.gravitation.Gravitation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.handle.HandleBlockEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * When a player uses the handle (shift + right-click) to move a physical structure,
 * injects a reaction force onto the SubLevel the player is standing on.
 * <p>
 * Physical rationale: the spring constraint pulls SubLevel A toward the player.
 * The player's feet transmit the equal-and-opposite reaction force to platform B.
 */
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.handle.HandleBlockEntity$HandleConstraint")
public class HandleBlockEntityConstraintMixin {

    /**
     * Reference to the enclosing HandleBlockEntity instance (Java synthetic field).
     */
    @Shadow(aliases = "this$0")
    @Final
    private HandleBlockEntity outer;

    /**
     * The player UUID who is holding this handle.
     */
    @Final
    @Shadow
    private UUID playerId;

    /**
     * The distance the player wants to keep from the handle. -1 means "normal hold" (no constraint).
     */
    @Shadow
    private float scrollDistance;

    /**
     * The active physics constraint handle, null if no constraint was applied this tick.
     */
    @Shadow
    @Nullable
    private PhysicsConstraintHandle constraintHandle;

    @Inject(method = "physicsTick", at = @At("RETURN"))
    private void gravitation$applyReactionForceToStandingSubLevel(
        ServerSubLevel subLevel,
        RigidBodyHandle handle,
        CallbackInfo ci
    ) {
        // Only apply when a spring constraint was actually created this tick.
        // constraintHandle is null if physicsTick returned early (scrollDistance == -1, or out of range).
        if (this.constraintHandle == null) return;

        double multiplier = Gravitation.CONFIG.handleReactionForceMultiplier;
        if (multiplier <= 0.0) return;

        assert outer.getLevel() != null;
        final Player player = outer.getLevel().getPlayerByUUID(this.playerId);
        if (player == null) return;

        // Find the SubLevel the player is standing/tracking on.
        final SubLevel standingSubLevel = Sable.HELPER.getTrackingSubLevel(player);
        // Ignore if the player is on the same SubLevel as the handle, or not on any SubLevel.
        if (standingSubLevel == null || standingSubLevel == subLevel) return;
        if (!(standingSubLevel instanceof final ServerSubLevel serverStandingSubLevel)) return;

        // ---- Compute force direction in world (display) space ----
        // constraintGoal: the spring target – player eye position + look direction * distance
        // (player coords are in display world space)
        final Vector3d constraintGoal = JOMLConversion.toJOML(
            player.getEyePosition().add(player.getLookAngle().scale(Math.max(2.0, this.scrollDistance))));

        // Handle anchor: transform from SubLevel A's plot coords to display world coords
        final Vector3d worldHandlePos = subLevel.logicalPose()
            .transformPosition(new Vector3d(outer.getGrabCenter()));

        // Reaction direction: from goal toward handle (i.e., the direction the spring pulls the player)
        final Vector3d reactionDir = new Vector3d(worldHandlePos).sub(constraintGoal);
        final double displacement = reactionDir.length();
        if (displacement < 1e-4) return;

        // Impulse magnitude proportional to spring displacement (stiffness from HandleConstraint) * multiplier
        // Converted to standing SubLevel's local frame via inverse normal transform
        reactionDir.normalize().mul(displacement * 2.2 * multiplier);
        final Vector3d localReactionDir = serverStandingSubLevel.logicalPose()
            .transformNormalInverse(reactionDir);

        // Obtain a rigid-body handle for the standing SubLevel
        final RigidBodyHandle standingHandle = RigidBodyHandle.of(serverStandingSubLevel);
        if (standingHandle == null || !standingHandle.isValid()) return;

        // Apply the reaction force as a linear impulse at the player's feet (plot coords of standing SubLevel)
        final Vector3d localPlayerPos = serverStandingSubLevel.logicalPose()
            .transformPositionInverse(JOMLConversion.toJOML(player.position()));
        standingHandle.applyImpulseAtPoint(localPlayerPos, localReactionDir);
    }
}

