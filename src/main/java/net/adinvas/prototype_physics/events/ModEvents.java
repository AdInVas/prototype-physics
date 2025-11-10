package net.adinvas.prototype_physics.events;

import net.adinvas.prototype_physics.PlayerPhysics;
import net.adinvas.prototype_physics.RagdollPart;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import javax.vecmath.Vector3f;

public class ModEvents {

    public static void onPlayerPartHit(ServerPlayer player, PlayerPhysics.Mode mode, RagdollPart partName, Object hitTarget, Vector3f point, Vector3f local, float impact) {
        PlayerPartHitEvent event = new PlayerPartHitEvent(player,mode, partName, hitTarget, point,local, impact);
        MinecraftForge.EVENT_BUS.post(event);
    }

    public static boolean onRagdollModeChange(ServerPlayer player, RagdollModeChangeEvent.Mode newMode) {
        RagdollModeChangeEvent event = new RagdollModeChangeEvent(player, newMode);
        MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled(); // return false if any listener canceled it
    }

    public static void onPlayerRagdollClick(Player Source, ServerPlayer Target, RagdollPart limb, Vector3f hitpos,Vector3f hitposNormal){
        RagdollClickEvent event = new RagdollClickEvent(Source,Target,limb,hitpos);
        MinecraftForge.EVENT_BUS.post(event);
    }

}
