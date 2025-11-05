package net.adinvas.prototype_physics.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("move")
    void invokermove(double p_90569_, double p_90570_, double p_90571_);

    @Invoker("setPosition")
    void invokerSetPosition(double p_90585_, double p_90586_, double p_90587_);
}
