package net.adinvas.prototype_physics.mixin;

import net.adinvas.prototype_physics.duck.CameraDuck;
import net.minecraft.client.Camera;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.*;

@Mixin(Camera.class)
public class CameraMixin implements CameraDuck {
    @Shadow @Final private Quaternionf rotation;

    @Override
    public void prototype_physics$copyRotation(Quaternionf q){
        if (q!=null){
            this.rotation.set(q);
        }
    }


}
