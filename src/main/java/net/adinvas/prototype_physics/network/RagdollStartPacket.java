package net.adinvas.prototype_physics.network;

import net.adinvas.prototype_physics.RagdollTransform;
import net.adinvas.prototype_physics.client.RagdollManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RagdollStartPacket {
    private final int playerId;
    private final int ragdollId;
    private final RagdollTransform[] transforms;

    public RagdollStartPacket(int playerId, int ragdollId, RagdollTransform[] transforms) {
        this.playerId = playerId;
        this.ragdollId = ragdollId;
        this.transforms = transforms;
    }

    public static void encode(RagdollStartPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.playerId);
        buf.writeInt(msg.ragdollId);
        buf.writeInt(msg.transforms.length);
        for (RagdollTransform t : msg.transforms) t.writeTo(buf);
    }

    public static RagdollStartPacket decode(FriendlyByteBuf buf) {
        int playerId = buf.readInt();
        int ragdollId = buf.readInt();
        int len = buf.readInt();
        RagdollTransform[] transforms = new RagdollTransform[len];
        for (int i = 0; i < len; i++) transforms[i] = RagdollTransform.readFrom(buf);
        return new RagdollStartPacket(playerId, ragdollId, transforms);
    }

    public static void handle(RagdollStartPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(RagdollStartPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RagdollManager.addOrUpdate(msg.playerId, msg.ragdollId, msg.transforms, System.currentTimeMillis());
    }
}
