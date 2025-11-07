package net.adinvas.prototype_physics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.adinvas.prototype_physics.RagdollPart;
import net.adinvas.prototype_physics.RagdollTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(value = Dist.CLIENT,bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RagdollPlayerRenderer {
    private static final Minecraft mc = Minecraft.getInstance();


    static final Vector3f[] headoff = new Vector3f[]{
            new Vector3f(0.0f, 0.0f, 0.0f),
            new Vector3f(0.0f, -6.0f/16, 0.0f)
    };
    static final Vector3f[] torsoff = new Vector3f[]{
            new Vector3f(0.0f, 0.0f, 0.0f),
            new Vector3f(0.0f, -6.0f/16, 0.0f)
    };
    static final Vector3f[] larmoff = new Vector3f[]{
            new Vector3f(3.8F, 4.0f, 0.0f),
            new Vector3f(1F/16, -7.0f/16, 0.0f)
    };
    static final Vector3f[] rarmoff = new Vector3f[]{
            new Vector3f(-3.8F, 4.0f, 0.0f),
            new Vector3f(-1F/16, -7.0f/16, 0.0f)
    };
    static final Vector3f[] llegoff = new Vector3f[]{
            new Vector3f(1.9f, 5.5f, 0.0f),
            new Vector3f(0.0f, 0f/16, 0.0f)
    };
    static final Vector3f[] rlegoff = new Vector3f[]{
            new Vector3f(-1.9f, 5.5f, 0.0f),
            new Vector3f(0.00f, 0f/16, 0.0f),
    };
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
        RagdollManager.ClientRagdoll rag = RagdollManager.get(player.getId());
        if (rag == null || !rag.isActive()) return;

        event.setCanceled(true); // We’ll manually render the player model

        PlayerRenderer renderer = event.getRenderer();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();

        float partial = event.getPartialTick();
        RagdollTransform torso = rag.getPartInterpolated(RagdollPart.TORSO, partial);
        RagdollTransform head = rag.getPartInterpolated(RagdollPart.HEAD, partial);
        RagdollTransform larm = rag.getPartInterpolated(RagdollPart.LEFT_ARM, partial);
        RagdollTransform rarm = rag.getPartInterpolated(RagdollPart.RIGHT_ARM, partial);
        RagdollTransform lleg = rag.getPartInterpolated(RagdollPart.LEFT_LEG, partial);
        RagdollTransform rleg = rag.getPartInterpolated(RagdollPart.RIGHT_LEG, partial);
        if (torso == null) return;

        ResourceLocation skin = player.getSkinTextureLocation();
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucent(skin));

        // Render just one part for now (e.g. torso)
        renderRagdollPart(poseStack, vertexConsumer, player, model.body, torso, torso,torsoff, event.getPackedLight());
        renderRagdollPart(poseStack, vertexConsumer, player, model.head, head, torso,headoff, event.getPackedLight());
        renderRagdollPart(poseStack, vertexConsumer, player, model.leftLeg, lleg, torso,llegoff, event.getPackedLight());
        renderRagdollPart(poseStack, vertexConsumer, player, model.rightLeg, rleg, torso,rlegoff, event.getPackedLight());
        renderRagdollPart(poseStack, vertexConsumer, player, model.leftArm, larm, torso,larmoff, event.getPackedLight());
        renderRagdollPart(poseStack, vertexConsumer, player, model.rightArm, rarm, torso,rarmoff, event.getPackedLight());
    }


    public static void renderRagdollPart(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            AbstractClientPlayer player,
            ModelPart part,
            RagdollTransform transform,
            RagdollTransform torso,
            Vector3f[] pivot,
            int light
    ) {
        if (transform == null) return;
        part.setPos(pivot[0].x,pivot[0].y,pivot[0].z);
        poseStack.pushPose();


        // Translate to physics position relative to player

        // --- Step 2: Move to the model part’s vanilla pivot ---
        Quaternionf torsoRot = new Quaternionf(
                torso.rotation.x,
                torso.rotation.y,
                torso.rotation.z,
                torso.rotation.w
        );

        Vector3f rotatedPivot = new Vector3f(pivot[1]);
        torsoRot.transform(rotatedPivot);

        // Apply quaternion rotation (world → model space)
        Quaternionf q = new Quaternionf(
                transform.rotation.x,
                transform.rotation.y,
                transform.rotation.z,
                transform.rotation.w
        );

        // Flip Z to match Minecraft’s coordinate system
        //poseStack.translate(-pivot.x,-pivot.y,-pivot.z);
        poseStack.translate(-rotatedPivot.x, -rotatedPivot.y, -rotatedPivot.z);
        q.rotateZ((float) Math.PI);
        poseStack.mulPose(q);

        //poseStack.translate(pivot.x,pivot.y,pivot.z);
        // Render part
        part.render(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }



}
