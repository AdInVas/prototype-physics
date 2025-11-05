package net.adinvas.prototype_physics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.adinvas.prototype_physics.RagdollPart;
import net.adinvas.prototype_physics.RagdollTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.vecmath.Quat4f;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber(value = Dist.CLIENT,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RagdollPlayerRenderer {
    private static final Minecraft mc = Minecraft.getInstance();

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
     RagdollManager.ClientRagdoll rag = RagdollManager.get(player.getId());
     if (rag == null || !rag.isActive()) return;
     PlayerRenderer renderer = event.getRenderer();
     PlayerModel<AbstractClientPlayer> originalModel = renderer.getModel();
     event.setCanceled(true);
     RagdollTransform head = rag.getPartInterpolated(RagdollPart.HEAD,event.getPartialTick());
     Vector3f rot = quaternionToEuler(head.rotation);
     originalModel.head.setRotation(rot.z,0,0);
     ResourceLocation texture = player.getSkinTextureLocation();
     VertexConsumer yes =event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(texture));
     originalModel.head.render(event.getPoseStack(),yes, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
    }

    public static Vector3f quaternionToEuler(Quat4f q) {
        // Normalize the quaternion
        q.normalize();

        // Extract components
        float x = q.x;
        float y = q.y;
        float z = q.z;
        float w = q.w;

        // Compute Euler angles (in radians)
        // Pitch (X-axis rotation)
        float sinr_cosp = 2.0f * (w * x + y * z);
        float cosr_cosp = 1.0f - 2.0f * (x * x + y * y);
        float pitch = (float) Math.atan2(sinr_cosp, cosr_cosp);

        // Yaw (Y-axis rotation)
        float sinp = 2.0f * (w * y - z * x);
        float yaw;
        if (Math.abs(sinp) >= 1)
            yaw = (float) Math.copySign(Math.PI / 2, sinp); // use 90° if out of range
        else
            yaw = (float) Math.asin(sinp);

        // Roll (Z-axis rotation)
        float siny_cosp = 2.0f * (w * z + x * y);
        float cosy_cosp = 1.0f - 2.0f * (y * y + z * z);
        float roll = (float) Math.atan2(siny_cosp, cosy_cosp);

        // Return as Vector3f (pitch, yaw, roll)
        return new Vector3f(pitch, yaw, roll);
    }

    private static void resetAllModelParts(PlayerModel<?> model) {
        // Body parts to reset
        model.head.loadPose(PartPose.ZERO);
        model.hat.loadPose(PartPose.ZERO);
        model.body.loadPose(PartPose.ZERO);
        model.leftArm.loadPose(PartPose.ZERO);
        model.rightArm.loadPose(PartPose.ZERO);
        model.leftLeg.loadPose(PartPose.ZERO);
        model.rightLeg.loadPose(PartPose.ZERO);
    }

}
