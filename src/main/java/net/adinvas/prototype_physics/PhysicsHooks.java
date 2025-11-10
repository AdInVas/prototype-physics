package net.adinvas.prototype_physics;

import net.adinvas.prototype_physics.events.PlayerPartHitEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PrototypePhysics.MODID)
public class PhysicsHooks {
    @SubscribeEvent
    public static void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            JbulletWorld manager = JbulletWorld.get(level);
            manager.step(1f / 20f); // Minecraft server ticks at 20 TPS
            JbulletWorld.get(level).reconcilePlayers();

// Ensure every player in this ServerLevel has an entry in this world's manager
            for (ServerPlayer sp : level.players()) {
                JbulletWorld.get(level).addPlayer(sp); // computeIfAbsent ensures idempotence
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            JbulletWorld.get(sp.serverLevel()).addPlayer(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            JbulletWorld.get(sp.serverLevel()).removePlayer(sp);
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        Vec3 explosionPos = event.getExplosion().getPosition();
        JbulletWorld world = JbulletWorld.get((ServerLevel) event.getLevel());
        // 1. This logic must run on the server
        if (level.isClientSide()) {
            return;
        }
        // 2. Create a bounding box 32 blocks in every direction from the explosion
        // This is a fast, efficient first-pass check.
        AABB checkBounds = new AABB(new BlockPos((int) explosionPos.x, (int) explosionPos.y, (int) explosionPos.z)).inflate(20);
        // 3. Get all players within that box
        for (Player player : level.getEntitiesOfClass(Player.class, checkBounds)) {
            // 4. Check the precise spherical distance
            double distance = player.position().distanceTo(explosionPos);
            if (distance > 20) {
                continue; // Player was in the corner of the AABB but > 32 blocks away
            }
            float distanceScale = (float) Math.pow(1-distance/20,2f);
            PlayerPhysics phys = world.getPlayerPhys((ServerPlayer) player);
            phys.applyExplosionImpulse(explosionPos,10*distanceScale);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event){
        if (event.getEntity() instanceof ServerPlayer player){
            JbulletWorld world = JbulletWorld.get(player.serverLevel());
            PlayerPhysics physics = world.getPlayerPhys(player);
            if (physics!=null){
                physics.setMode(PlayerPhysics.Mode.SILENT);
            }
        }
    }


    @SubscribeEvent
    public static void onRagollHit(PlayerPartHitEvent event){
        if (event.getImpactForce()<0.1)return;
        event.getPlayer().sendSystemMessage(Component.literal("VEL : "+event.getImpactForce()+" AT: "+event.getLocalPoint()));
    }

}
