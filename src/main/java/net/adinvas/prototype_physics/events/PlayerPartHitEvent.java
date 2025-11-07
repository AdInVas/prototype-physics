package net.adinvas.prototype_physics.events;

import net.adinvas.prototype_physics.PlayerPhysics;
import net.adinvas.prototype_physics.RagdollPart;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import javax.vecmath.Vector3f;

public class PlayerPartHitEvent extends Event {
    private final ServerPlayer player;
    private final RagdollPart partName;        // e.g. "head", "torso"
    private final Object hitTarget;       // BlockPos or Entity
    private final Vector3f contactPoint;
    private final float impactForce;
    private final PlayerPhysics.Mode mode;


    public PlayerPartHitEvent(ServerPlayer player, PlayerPhysics.Mode mode, RagdollPart partName, Object hitTarget, Vector3f contactPoint, float impactForce) {
        this.player = player;
        this.partName = partName;
        this.hitTarget = hitTarget;
        this.contactPoint = contactPoint;
        this.impactForce = impactForce;
        this.mode = mode;
    }

    public ServerPlayer getPlayer() { return player; }
    public RagdollPart getPartName() { return partName; }

    /** Either a BlockPos or Entity depending on what was hit. */
    public Object getHitTarget() { return hitTarget; }

    public Vector3f getContactPoint() { return contactPoint; }
    public float getImpactForce() { return impactForce; }

    public PlayerPhysics.Mode getMode() {
        return mode;
    }
}
