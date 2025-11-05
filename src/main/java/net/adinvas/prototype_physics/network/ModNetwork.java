package net.adinvas.prototype_physics.network;

import net.adinvas.prototype_physics.PrototypePhysics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PrototypePhysics.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );


    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        CHANNEL.registerMessage(nextId(), RagdollStartPacket.class,
                RagdollStartPacket::encode,
                RagdollStartPacket::decode,
                RagdollStartPacket::handle);

        CHANNEL.registerMessage(nextId(), RagdollUpdatePacket.class,
                RagdollUpdatePacket::encode,
                RagdollUpdatePacket::decode,
                RagdollUpdatePacket::handle);

        CHANNEL.registerMessage(nextId(), RagdollEndPacket.class,
                RagdollEndPacket::encode,
                RagdollEndPacket::decode,
                RagdollEndPacket::handle);
    }
}
