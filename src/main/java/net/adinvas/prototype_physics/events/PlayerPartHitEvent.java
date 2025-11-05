package net.adinvas.prototype_physics.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import javax.vecmath.Vector3f;

public class PlayerPartHitEvent extends Event {
    private final ServerPlayer player;
    private final String partName;        // e.g. "head", "torso"
    private final Object hitTarget;       // BlockPos or Entity
    private final Vector3f contactPoint;
    private final float impactForce;


    public PlayerPartHitEvent(ServerPlayer player, String partName, Object hitTarget, Vector3f contactPoint, float impactForce) {
        this.player = player;
        this.partName = partName;
        this.hitTarget = hitTarget;
        this.contactPoint = contactPoint;
        this.impactForce = impactForce;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getPartName() { return partName; }

    /** Either a BlockPos or Entity depending on what was hit. */
    public Object getHitTarget() { return hitTarget; }

    public Vector3f getContactPoint() { return contactPoint; }
    public float getImpactForce() { return impactForce; }

}
