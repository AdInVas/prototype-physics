package net.adinvas.prototype_physics.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import javax.vecmath.Vector3f;

public class ModEvents {

    public static void onPlayerPartHit(ServerPlayer player, String partName, Object hitTarget, Vector3f point, float impact) {
        PlayerPartHitEvent event = new PlayerPartHitEvent(player, partName, hitTarget, point, impact);
        MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onRagdollModeChange(ServerPlayer player, RagdollModeChangeEvent.Mode newMode) {
        RagdollModeChangeEvent event = new RagdollModeChangeEvent(player, newMode);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled(); // return false if any listener canceled it
    }
}
