package net.adinvas.prototype_physics.events;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class BasicTextEvent {
    @SubscribeEvent
    public static void onPartHit(PlayerPartHitEvent event) {
        var player = event.getPlayer();
        var part = event.getPartName();
        System.out.printf("%s's %s collided at %s (impact=%.2f)%n",
                player.getName().getString(),
                part,
                event.getContactPoint(),
                event.getImpactForce());
        // e.g., apply damage, play sound, spawn particles, etc.
    }

    @SubscribeEvent
    public static void onModeChange(RagdollModeChangeEvent event) {
        System.out.println(event.getPlayer().getName().getString() + " -> " + event.getNewMode());
        // You can cancel:
        // if (event.getNewMode() == Mode.PRECISE && !event.getPlayer().isOp()) event.setCanceled(true);
    }
}
