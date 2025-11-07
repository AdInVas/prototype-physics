package net.adinvas.prototype_physics.events;

import com.bulletphysics.collision.dispatch.CollisionWorld;
import com.bulletphysics.dynamics.RigidBody;
import net.adinvas.prototype_physics.JbulletWorld;
import net.adinvas.prototype_physics.PlayerPhysics;
import net.adinvas.prototype_physics.PrototypePhysics;
import net.adinvas.prototype_physics.RagdollPart;
import net.adinvas.prototype_physics.client.RagdollManager;
import net.adinvas.prototype_physics.network.PlayerClickRaycastPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

@Mod.EventBusSubscriber
public class BasicTextEvent {

    @SubscribeEvent
    public static void onModeChange(RagdollModeChangeEvent event) {
        System.out.println(event.getPlayer().getName().getString() + " -> " + event.getNewMode());
        // You can cancel:
        // if (event.getNewMode() == Mode.PRECISE && !event.getPlayer().isOp()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPPI(RagdollClickEvent event){
        var player = event.getSource();
        var part = event.getTarget();
        System.out.printf("%s| %s| %s| %s",
                player.getName().getString(),
                part.getName().getString(),
                event.getPartName(),
                event.getContactPoint()
        );
        // e.g., apply damage, play sound, spawn particles, etc.
    }

    @SubscribeEvent
    public static void test(RagdollClickEvent event){
        PrototypePhysics.LOGGER.info("{}| {}",event.getTarget(),event.getPartName());
    }
}
