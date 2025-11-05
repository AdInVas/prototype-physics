package net.adinvas.prototype_physics.network;

import net.adinvas.prototype_physics.RagdollTransform;
import net.adinvas.prototype_physics.client.RagdollManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RagdollUpdatePacket {
    private final int ragdollId;
    private final long timestamp;
    private final RagdollTransform[] transforms;

    public RagdollUpdatePacket(int ragdollId, long timestamp, RagdollTransform[] transforms) {
        this.ragdollId = ragdollId;
        this.timestamp = timestamp;
        this.transforms = transforms;
    }

    public static void encode(RagdollUpdatePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.ragdollId);
        buf.writeLong(msg.timestamp);
        buf.writeInt(msg.transforms.length);
        for (RagdollTransform t : msg.transforms) t.writeTo(buf);
    }

    public static RagdollUpdatePacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        long ts = buf.readLong();
        int len = buf.readInt();
        RagdollTransform[] transforms = new RagdollTransform[len];
        for (int i = 0; i < len; i++) transforms[i] = RagdollTransform.readFrom(buf);
        return new RagdollUpdatePacket(id, ts, transforms);
    }

    public static void handle(RagdollUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(RagdollUpdatePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RagdollManager.addOrUpdate(msg.ragdollId, msg.ragdollId, msg.transforms, msg.timestamp);
    }
}