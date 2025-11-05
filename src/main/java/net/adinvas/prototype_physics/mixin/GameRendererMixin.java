package net.adinvas.prototype_physics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.adinvas.prototype_physics.RagdollPart;
import net.adinvas.prototype_physics.RagdollTransform;
import net.adinvas.prototype_physics.client.RagdollManager;
import net.adinvas.prototype_physics.duck.CameraDuck;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow @Final private Camera mainCamera;

    public GameRendererMixin(){
    }


    private Quaternionf oldQ = new Quaternionf();
    private Vec3 lastpos=  null;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", shift = At.Shift.AFTER))
    private void afterCameraSetup(float partialTick, long nanoTime, PoseStack poseStack, CallbackInfo ci){
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var rag = RagdollManager.get(mc.player.getId());
        if (rag == null || !rag.isActive()) {
            lastpos = null;
            return;
        }

        RagdollTransform head = rag.getPartInterpolated(RagdollPart.HEAD, partialTick);
        if (head == null) return;

        // --- Smooth head position ---
        Vec3 headPos = new Vec3(head.position.x, head.position.y, head.position.z);
        if (lastpos == null) lastpos = headPos;
        lastpos = lastpos.lerp(headPos, 0.2f);

        // --- Smooth rotation ---
        Quaternionf q = new Quaternionf(head.rotation.x, head.rotation.y, head.rotation.z, head.rotation.w);
        oldQ.slerp(q, 0.2f);

        // --- Get current camera ---
        var camera = mc.gameRenderer.getMainCamera();
        var camType = mc.options.getCameraType();

        // --- Default target = head position ---
        Vector3f target = new Vector3f((float) lastpos.x, (float) lastpos.y, (float) lastpos.z);

        // --- Apply third-person offset if needed ---
        if (!camType.isFirstPerson()) {
            // Default camera distance
            float distance = 4.0f;
            // Flip distance for front-view camera
            if (camType.isMirrored()) distance = -distance;

            // Offset vector along local Z
            Vector3f offset = new Vector3f(0f, 0f, distance);

            // Rotate offset by head rotation
            offset.rotate(q);

            // Add to target
            target.add(offset);
        }

        // --- Smooth move camera toward new target ---
        Vec3 current = camera.getPosition();
        Vec3 newPos = new Vec3(target.x, target.y, target.z);
        Vec3 smooth = current.lerp(newPos, 0.3f);

        // --- Apply movement ---
        ((CameraAccessor) camera).invokermove(
                smooth.x - current.x,
                smooth.y - current.y,
                smooth.z - current.z
        );

        // --- Apply rotation ---
        ((CameraDuck) camera).prototype_physics$copyRotation(oldQ);
    }
}
