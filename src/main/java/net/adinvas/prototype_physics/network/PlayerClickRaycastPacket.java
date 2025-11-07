package net.adinvas.prototype_physics.network;

import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.CollisionWorld;
import com.bulletphysics.dynamics.RigidBody;
import net.adinvas.prototype_physics.JbulletWorld;
import net.adinvas.prototype_physics.PlayerPhysics;
import net.adinvas.prototype_physics.RagdollPart;
import net.adinvas.prototype_physics.events.ModEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import javax.vecmath.Vector3f;
import java.util.UUID;
import java.util.function.Supplier;

public class PlayerClickRaycastPacket {
    private final Vec3 start;
    private final Vec3 end;

    public PlayerClickRaycastPacket(Vec3 start, Vec3 end) {
        this.start = start;
        this.end = end;
    }

    public PlayerClickRaycastPacket(FriendlyByteBuf buf){
        Vec3 start = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        this.start = start;
        this.end = end;
    }

    public void write(FriendlyByteBuf buf){
        buf.writeDouble(start.x);
        buf.writeDouble(start.y);
        buf.writeDouble(start.z);
        buf.writeDouble(end.x);
        buf.writeDouble(end.y);
        buf.writeDouble(end.z);
    }


    public static void handle(PlayerClickRaycastPacket msg, Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->{
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            JbulletWorld world = JbulletWorld.get(player.serverLevel());
            Vector3f from = new Vector3f((float) msg.start.x, (float) msg.start.y, (float) msg.start.z);
            Vector3f to = new Vector3f((float) msg.end.x, (float) msg.end.y, (float) msg.end.z);

            CollisionWorld.ClosestRayResultCallback cb = new CollisionWorld.ClosestRayResultCallback(from, to);
            world.getDynamicsWorld().rayTest(from, to, cb);

            if (cb.hasHit()) {
                CollisionObject hit = cb.collisionObject;
                ServerPlayer hitOwner = world.getPlayerFromBody(hit);

                if (hitOwner != null) {
                    // Find which part was hit for the clicked player
                    PlayerPhysics phys = world.getPlayerPhys(hitOwner);
                    if (phys != null) {
                        RagdollPart part = phys.identifyPart((RigidBody) hit);

                        // Fire your custom event (you already have this)
                        ModEvents.onPlayerRagdollClick(hitOwner,player, part,
                                new Vector3f(cb.hitPointWorld),
                                new Vector3f(cb.hitNormalWorld));
                    }
                }
            }
        });

    }
}
