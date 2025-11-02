package net.adinvas.prototype_physics;

import com.bulletphysics.collision.dispatch.CollisionConfiguration;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.mojang.logging.LogUtils;


import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;

import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraftforge.fml.common.Mod;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
// The value here should match an entry in the META-INF/mods.toml file
@Mod(PrototypePhysics.MODID)
public class PrototypePhysics {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "prototype_physics";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public PrototypePhysics() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        CollisionConfiguration cc = new DefaultCollisionConfiguration();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }
    }



}
