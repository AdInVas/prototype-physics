package net.adinvas.prototype_physics.network;

import net.adinvas.prototype_physics.client.RagdollManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RagdollEndPacket {
    private final int ragdollId;

    public RagdollEndPacket(int ragdollId) {
        this.ragdollId = ragdollId;
    }

    public static void encode(RagdollEndPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.ragdollId);
    }

    public static RagdollEndPacket decode(FriendlyByteBuf buf) {
        return new RagdollEndPacket(buf.readInt());
    }

    public static void handle(RagdollEndPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(RagdollEndPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RagdollManager.remove(msg.ragdollId);
    }
}
