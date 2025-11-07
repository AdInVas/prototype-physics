package net.adinvas.prototype_physics.events;

import net.adinvas.prototype_physics.PrototypePhysics;
import net.adinvas.prototype_physics.network.ModNetwork;
import net.adinvas.prototype_physics.network.PlayerClickRaycastPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ClientEvents {

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickEmpty event){
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 start = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Vec3 end = start.add(look.scale(5));
        ModNetwork.CHANNEL.sendToServer(new PlayerClickRaycastPacket(start, end));
    }
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event){
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 start = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Vec3 end = start.add(look.scale(5));
        ModNetwork.CHANNEL.sendToServer(new PlayerClickRaycastPacket(start, end));
    }
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event){
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 start = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Vec3 end = start.add(look.scale(5));
        ModNetwork.CHANNEL.sendToServer(new PlayerClickRaycastPacket(start, end));
    }
}
