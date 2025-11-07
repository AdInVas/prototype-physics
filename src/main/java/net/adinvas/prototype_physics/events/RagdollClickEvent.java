package net.adinvas.prototype_physics.events;

import net.adinvas.prototype_physics.PlayerPhysics;
import net.adinvas.prototype_physics.RagdollPart;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

import javax.vecmath.Vector3f;

public class RagdollClickEvent extends Event {
    private final Player source;
    private final ServerPlayer target;
    private final RagdollPart partName;        // e.g. "head", "torso"
    private final Vector3f contactPoint;

    public RagdollClickEvent(Player source, ServerPlayer target, RagdollPart partName, Vector3f contactPoint) {
        this.source = source;
        this.target = target;
        this.partName = partName;
        this.contactPoint = contactPoint;
    }

    public Player getSource() {
        return source;
    }

    public RagdollPart getPartName() {
        return partName;
    }

    public ServerPlayer getTarget() {
        return target;
    }

    public Vector3f getContactPoint() {
        return contactPoint;
    }
}
