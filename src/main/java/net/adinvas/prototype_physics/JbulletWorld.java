package net.adinvas.prototype_physics;

import com.bulletphysics.collision.broadphase.AxisSweep3;
import com.bulletphysics.collision.broadphase.BroadphaseInterface;
import com.bulletphysics.collision.dispatch.CollisionConfiguration;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.constraintsolver.ConstraintSolver;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.vecmath.Vector3f;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JbulletWorld {

    private static final Map<ServerLevel, JbulletWorld> INSTANCES = new ConcurrentHashMap<>();

    public static JbulletWorld get(ServerLevel level) {
        // One manager per dimension
        return INSTANCES.computeIfAbsent(level, JbulletWorld::new);
    }
    private final ServerLevel level;


    private BroadphaseInterface broadphase;
    private CollisionConfiguration collisionConfig;
    private CollisionDispatcher dispatcher;
    private ConstraintSolver solver;
    private DiscreteDynamicsWorld dynamicsWorld;

    private final Map<UUID, PlayerPhysics> players = new ConcurrentHashMap<>();

    public JbulletWorld(ServerLevel level) {
        this.level = level;
        collisionConfig = new DefaultCollisionConfiguration();
        dispatcher = new CollisionDispatcher(collisionConfig);

        // Large but limited world AABB — still not doing full world collisions
        Vector3f worldAabbMin = new Vector3f(-10000f, -10000f, -10000f);
        Vector3f worldAabbMax = new Vector3f(10000f, 10000f, 10000f);
        broadphase = new AxisSweep3(worldAabbMin, worldAabbMax);

        solver = new SequentialImpulseConstraintSolver();

        dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);
        dynamicsWorld.setGravity(new Vector3f(0f, -9.81f, 0f));
        dynamicsWorld.getSolverInfo().numIterations = 20;
    }

    public void step(float dt) {
        // fixed-step, with substeps
        dynamicsWorld.stepSimulation(dt, 30, 1f/120f);

        // read back player positions & dispatch events
        for (PlayerPhysics pp : players.values()) {
            pp.afterStep(); // will push network updates, handle events
        }

        reconcilePlayers();
    }

    public DiscreteDynamicsWorld getDynamicsWorld() {
        return dynamicsWorld;
    }

    public void addPlayer(ServerPlayer player) {
        // Avoid recreating PlayerPhysics if it already exists for this player in this JbulletWorld
        players.computeIfAbsent(player.getUUID(), uuid -> new PlayerPhysics(player, this, dynamicsWorld));
    }

    public void removePlayer(ServerPlayer player) {
        PlayerPhysics pp = players.remove(player.getUUID());
        if (pp != null) pp.destroy();
    }

    public PlayerPhysics getPlayerPhys(ServerPlayer player){
        return players.get(player.getUUID());
    }

    public CollisionDispatcher getDispatcher() { return dispatcher; }
    public DiscreteDynamicsWorld getWorld() { return dynamicsWorld; }

    public ServerPlayer getPlayerFromBody(CollisionObject obj) {
        for (PlayerPhysics pp : players.values()) {
            if (pp.hasBody(obj)) {
                return pp.getPlayer();
            }
        }
        return null;
    }

    public void reconcilePlayers() {
        // Iterate over a copy of entries to avoid concurrent-modification
        var it = players.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            PlayerPhysics pp = entry.getValue();
            ServerPlayer player = pp.getPlayer();
            // If player's current serverLevel is not the same as this manager's level,
            // they moved dimension => remove their PlayerPhysics from this world.
            if (player == null || player.serverLevel() != this.level) {
                pp.destroy();
                it.remove();
            }
        }
    }

    public ServerPlayer getServerPlayer(UUID uuid) {
        // may return null if player is offline / respawned into different world
        for (ServerPlayer p : level.players()) {
            if (p.getUUID().equals(uuid)) return p;
        }
        // faster alternative: level.getPlayerByUUID(uuid) if available in your MCP/MC version
        return (ServerPlayer) level.getPlayerByUUID(uuid);
    }

}
