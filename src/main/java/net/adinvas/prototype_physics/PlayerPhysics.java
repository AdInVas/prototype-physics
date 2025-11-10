package net.adinvas.prototype_physics;

import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.CollisionWorld;
import com.bulletphysics.collision.dispatch.PairCachingGhostObject;
import com.bulletphysics.collision.narrowphase.ManifoldPoint;
import com.bulletphysics.collision.narrowphase.PersistentManifold;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.CompoundShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.dynamics.constraintsolver.TypedConstraint;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import net.adinvas.prototype_physics.events.ModEvents;
import net.adinvas.prototype_physics.events.RagdollClickEvent;
import net.adinvas.prototype_physics.network.ModNetwork;
import net.adinvas.prototype_physics.network.RagdollEndPacket;
import net.adinvas.prototype_physics.network.RagdollStartPacket;
import net.adinvas.prototype_physics.network.RagdollUpdatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Quaternionf;

import javax.vecmath.AxisAngle4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import java.util.*;
import java.util.function.BiFunction;

public class PlayerPhysics {
    public enum Mode { SILENT, PRECISE }

    private final UUID player;
    private final JbulletWorld manager;
    private final DiscreteDynamicsWorld world;
    private Mode mode = Mode.SILENT;
    private boolean ignoreNext;

    private final List<RigidBody> ragdollParts = new ArrayList<>(6);
    private final List<TypedConstraint> ragdollJoints = new ArrayList<>(5);

    private final List<CollisionObject> localStaticCollision = new ArrayList<>();

    private final int networkRagdollId;

    public PlayerPhysics(ServerPlayer player, JbulletWorld manager, DiscreteDynamicsWorld world) {
        this.player = player.getUUID();
        this.manager = manager;
        this.world = world;
        this.networkRagdollId = player.getId(); // simple choice; use a unique id scheme in production
        createSilentBodies();
        updateLocalWorldCollision(); // build initial 5x5x5
    }
    private ServerPlayer getPlayerSafe() {
        return manager.getServerPlayer(player);
    }

    private void createSilentBodies() {
        destroySilentBodies(); // cleanup if any exist first
        ServerPlayer player = getPlayerSafe();
        if (player==null)return;
        Vector3d pos = new Vector3d(player.getX(), player.getY(), player.getZ());
        float yawRad = (float) Math.toRadians(player.getYHeadRot());

        // Basic shape sizes (same as ragdoll)
        CollisionShape torsoShape = new BoxShape(new Vector3f(0.25f, 0.4f, 0.15f));
        CollisionShape headShape = new BoxShape(new Vector3f(0.2f, 0.2f, 0.2f));
        CollisionShape limbShape = new BoxShape(new Vector3f(0.15f, 0.45f, 0.15f));

        // Create each as kinematic rigid bodies
        silentParts.clear();

        silentParts.add(createKinematicBody(torsoShape, pos.x, pos.y + 1.0f, pos.z, yawRad)); // torso
        silentParts.add(createKinematicBody(headShape, pos.x, pos.y + 1.75f, pos.z, yawRad)); // head
        silentParts.add(createKinematicBody(limbShape, pos.x - 0.2f, pos.y + 0.45f, pos.z, yawRad)); // left leg
        silentParts.add(createKinematicBody(limbShape, pos.x + 0.2f, pos.y + 0.45f, pos.z, yawRad)); // right leg
        silentParts.add(createKinematicBody(limbShape, pos.x - 0.35f, pos.y + 1.3f, pos.z, yawRad)); // left arm
        silentParts.add(createKinematicBody(limbShape, pos.x + 0.35f, pos.y + 1.3f, pos.z, yawRad)); // right arm
    }

    private final List<RigidBody> silentParts = new ArrayList<>();

    private RigidBody createKinematicBody(CollisionShape shape, double x, double y, double z, float yaw) {
        Transform t = new Transform();
        t.setIdentity();

        // apply yaw rotation (convert to quaternion)
        Quat4f rot = new Quat4f(0, (float) Math.sin(yaw / 2f), 0, (float) Math.cos(yaw / 2f));
        t.setRotation(rot);
        t.origin.set((float) x, (float) y, (float) z);

        RigidBodyConstructionInfo info = makeInfo(0f, t, shape);
        RigidBody body = new RigidBody(info);
        body.setCollisionFlags(body.getCollisionFlags() | CollisionFlags.KINEMATIC_OBJECT);
        body.setActivationState(CollisionObject.DISABLE_DEACTIVATION);
        world.addRigidBody(body);
        return body;
    }

    private void destroySilentBodies() {
        for (RigidBody r : silentParts) world.removeRigidBody(r);
        silentParts.clear();
    }

    private RigidBodyConstructionInfo makeInfo(float mass, Transform startTransform, CollisionShape shape) {
        Vector3f inertia = new Vector3f();
        if (mass > 0f) {
            // Properly compute inertia based on shape and mass
            shape.calculateLocalInertia(mass, inertia);
        } else {
            inertia.set(0, 0, 0);
        }

        DefaultMotionState motionState = new DefaultMotionState(startTransform);
        RigidBodyConstructionInfo info = new RigidBodyConstructionInfo(mass, motionState, shape, inertia);

        // Reasonable physical parameters
        info.linearDamping = 0.04f;
        info.angularDamping = 0.85f;
        info.restitution = 0.0f;
        info.friction = 0.9f;
        info.additionalDamping = true;

        return info;
    }

    public void setMode(Mode newMode) {
        if (newMode == this.mode) return;
        if (newMode == Mode.PRECISE) {
            ServerPlayer player = getPlayerSafe();
            if (isInsideSolid(player,manager.getWorld()))return;
            enterRagdollMode();
        } else {
            exitRagdollMode();
        }
        this.mode = newMode;
    }

    public void forceMode(Mode newmode){
        if (newmode == this.mode) return;
        if (newmode == Mode.PRECISE) {
            enterRagdollMode();
        } else {
            exitRagdollMode();
        }
        this.mode = newmode;
    }

    public Mode getMode() {
        return mode;
    }

    private void enterRagdollMode() {
        // remove silent bodies
        ServerPlayer player = getPlayerSafe();
        if (player==null)return;
        destroySilentBodies();
        // create ragdoll parts (6 bodies). Example layout: head, torso, pelvis, leftArm, rightArm, legs combined maybe
        // massed bodies

        createRagdollBodies();
        // create joints: e.g. pelvis <-> torso, torso <-> head, torso <-> leftArm, torso <-> rightArm, pelvis <-> legs (or separate)

        // inform clients to spawn ragdoll visuals
        ModNetwork.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new RagdollStartPacket(player.getId(), networkRagdollId, getRagdollTransforms())
        );
    }
    private boolean isInsideSolid(ServerPlayer player, DiscreteDynamicsWorld world) {
        // Define a small bounding shape roughly matching the player torso
        CollisionShape shape = new BoxShape(new Vector3f(0.4f, 0.9f, 0.4f));

        Transform t = new Transform();
        t.setIdentity();
        t.origin.set(
                (float) player.getX(),
                (float) player.getY() + 0.9f,
                (float) player.getZ()
        );

        // Create a temporary ghost object to test collisions
        PairCachingGhostObject ghost = new PairCachingGhostObject();
        ghost.setWorldTransform(t);
        ghost.setCollisionShape(shape);
        ghost.setCollisionFlags(CollisionFlags.NO_CONTACT_RESPONSE);

        // Register ghost temporarily in the world
        world.addCollisionObject(ghost);

        // Check overlaps
        boolean inside = world.getDispatcher().getNumManifolds() > 0;
        for (int i = 0; i < world.getDispatcher().getNumManifolds(); i++) {
            PersistentManifold m = world.getDispatcher().getManifoldByIndexInternal(i);
            if (m.getBody0() == ghost || m.getBody1() == ghost) {
                if (m.getNumContacts() > 0) {
                    inside = true;
                    break;
                }
            }
        }

        // Remove ghost again
        world.removeCollisionObject(ghost);
        return inside;
    }

    private void exitRagdollMode() {
        // remove ragdoll joint + parts
        for (TypedConstraint c : ragdollJoints) world.removeConstraint(c);
        for (RigidBody r : ragdollParts) world.removeRigidBody(r);
        ragdollJoints.clear();
        ragdollParts.clear();

        // re-add silent bodies and place them at player's current location
        createSilentBodies();

        ModNetwork.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new RagdollEndPacket(networkRagdollId)
        );
    }

    public void afterStep() {
        if (mode == Mode.SILENT) {
            // update silent kinematic bodies from player server pos/vel
            updateSilentFromPlayer();
            // you can sample intersections quickly here

        } else if (mode == Mode.PRECISE) {
            // read ragdoll transforms
            ServerPlayer player = getPlayerSafe();
            if (player==null)return;
            RagdollTransform[] transforms = getRagdollTransforms();
            ModNetwork.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY.with(()->player),
                    new RagdollUpdatePacket(networkRagdollId, System.currentTimeMillis(), transforms)
            );
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(()->player),
                    new RagdollUpdatePacket(networkRagdollId, System.currentTimeMillis(), transforms)
            );

            // collision detection: we attach custom ContactResultCallback or poll manifolds
            RigidBody torsoBody = ragdollParts.get(0);
            Transform torsoTransform = new Transform();
            torsoBody.getMotionState().getWorldTransform(torsoTransform);
            Quaternionf q = new Quaternionf().rotateXYZ(0,player.getYRot(),0);
            Quat4f qq = new Quat4f(q.x,q.y,q.z,q.w);
            //torsoTransform.setRotation(qq);
            Vector3f pos = torsoTransform.origin;
            player.teleportTo(pos.x, pos.y, pos.z);
            //torsoBody.setWorldTransform(torsoTransform);
            for (RigidBody r :ragdollParts){
                Vector3f vel = new Vector3f();
                r.getLinearVelocity(vel);
                if (vel.y < -80f) vel.y = -80f;
                float speed = vel.length();
                if (speed > 90f) {
                    vel.scale(90f / speed);
                }
                r.setLinearVelocity(vel);
            }
            applyFluidForces();
        }
        checkCollisionsForParts();
        updateLocalWorldCollision();
        correctInterpenetrations();
    }
    private void updateSilentFromPlayer() {
        Transform t = new Transform();
        t.setIdentity();
        ServerPlayer player = getPlayerSafe();
        if (player==null)return;
        // Base player position and velocity
        Vec3 vel = player.getDeltaMovement();
        Vector3d ppos = new Vector3d(player.getX(), player.getY(), player.getZ());

        // Predict forward by a short time (e.g., 0.15 seconds)
        double predictionTime = 0.15; // seconds ahead
        Vector3d predicted = new Vector3d(
                ppos.x + vel.x * predictionTime * 20, // multiply by 20 because MC ticks at 20/sec
                ppos.y + vel.y * predictionTime * 20,
                ppos.z + vel.z * predictionTime * 20
        );

        // Rotation
        Quaternionf q = new Quaternionf().rotateXYZ(0, player.getYRot() * Mth.DEG_TO_RAD, 0);
        Quat4f qq = new Quat4f(q.x, q.y, q.z, q.w);
        t.setRotation(qq);

        // Set transform to predicted position
        t.origin.set((float) predicted.x, (float) predicted.y, (float) predicted.z);

        for (RigidBody r : silentParts) {
            r.setWorldTransform(t);
            r.setInterpolationWorldTransform(t); // helps prevent Bullet interpolation artifacts
        }

        // Optional: run predictive collision test
        checkCollisionsForParts();
    }
    public void applyVelocityToWhole(Vector3f vel){
        for (RigidBody r:ragdollParts){
            r.setLinearVelocity(vel);
        }
    }

    private Quat4f getPlayerRotation(ServerPlayer player) {
        // Minecraft yaw is around Y, pitch around X
        float yaw = (float) Math.toRadians(-player.getYRot());
        float pitch = (float) Math.toRadians(player.getXRot());

        Quat4f qYaw = new Quat4f();
        qYaw.set(new AxisAngle4f(0, 1, 0, yaw));

        Quat4f qPitch = new Quat4f();
        qPitch.set(new AxisAngle4f(1, 0, 0, pitch));

        Quat4f result = new Quat4f();
        result.mul(qYaw, qPitch); // yaw * pitch
        return result;
    }

    private void createRagdollBodies() {
        ServerPlayer player = getPlayerSafe();
        if (player==null)return;
        Vec3 plM = player.getDeltaMovement(); // current player velocity
        Vector3f motion = new Vector3f((float) plM.x * 20, (float) plM.y * 20, (float) plM.z * 20);
        float x = 0;
        if (player.getPose() == Pose.SWIMMING) {
            x = 90;
        }

        Vector3d pos = new Vector3d(player.getX(), player.getY(), player.getZ());
        Quaternionf q = new Quaternionf().rotateXYZ(
                (float) Math.toRadians(x),
                (float) Math.toRadians(180 -player.getYRot()),
                0f
        );
        Quat4f qq = new Quat4f(q.x, q.y, q.z, q.w);

        Vector3f torsoOrigin = new Vector3f((float) pos.x, (float) pos.y + 1.3f, (float) pos.z);

        // Helper to rotate a local offset into world space
        java.util.function.Function<Vector3f, Vector3f> worldOffset = (local) -> {
            Vector3f result = new Vector3f(local);
            org.joml.Vector3f temp = new org.joml.Vector3f(result.x,result.y,result.z);
            q.transform(temp);
            result = new Vector3f(temp.x,temp.y,temp.z);
            result.add(torsoOrigin);
            return result;
        };

        // --- Pelvis / Torso ---
        CollisionShape torsoShape = new BoxShape(new Vector3f(0.25f, 0.4f, 0.15f));
        Transform torsoT = new Transform();
        torsoT.setIdentity();
        torsoT.setRotation(qq);
        torsoT.origin.set(torsoOrigin);
        RigidBody torsoBody = new RigidBody(makeInfo(8, torsoT, torsoShape));
        torsoBody.setLinearVelocity(motion);
        torsoBody.setRestitution(0.0f);
        torsoBody.setDamping(0.05f, 0.85f);
        torsoBody.setSleepingThresholds(0.1f, 0.1f);
        torsoBody.setCcdMotionThreshold(0.01f);
        torsoBody.setCcdSweptSphereRadius(0.4f);
        world.addRigidBody(torsoBody);
        ragdollParts.add(torsoBody);

        // --- Head ---
        CollisionShape headShape = new BoxShape(new Vector3f(0.2f, 0.2f, 0.2f));
        Vector3f headLocal = new Vector3f(0f, 0.55f, 0f); // relative to torso center 0.75
        Vector3f headPos = worldOffset.apply(headLocal);
        Transform headT = new Transform();
        headT.setIdentity();
        headT.origin.set(headPos);
        headT.setRotation(qq);
        RigidBody headBody = new RigidBody(makeInfo(4, headT, headShape));
        headBody.setLinearVelocity(motion);
        headBody.setCcdMotionThreshold(0.01f);
        headBody.setDamping(0.05f, 0.85f);
        headBody.setCcdSweptSphereRadius(0.25f);
        world.addRigidBody(headBody);
        ragdollParts.add(headBody);

        // --- Left Leg ---
        CollisionShape lLegShape = new BoxShape(new Vector3f(0.15f, 0.45f, 0.15f));
        Vector3f lLegLocal = new Vector3f(-0.1f, -0.75f, 0f);
        Vector3f lLegPos = worldOffset.apply(lLegLocal);

        Transform lLegT = new Transform();
        lLegT.setIdentity();
        lLegT.origin.set(lLegPos);
        lLegT.setRotation(qq);
        RigidBody lLegBody = new RigidBody(makeInfo(6, lLegT, lLegShape));
        lLegBody.setLinearVelocity(motion);
        lLegBody.setCcdMotionThreshold(0.01f);
        lLegBody.setDamping(0.05f, 0.85f);
        lLegBody.setCcdSweptSphereRadius(0.35f);
        world.addRigidBody(lLegBody);
        ragdollParts.add(lLegBody);

        // --- Right Leg ---
        Vector3f rLegLocal = new Vector3f(0.1f, -0.75f, 0f);
        Vector3f rLegPos = worldOffset.apply(rLegLocal);
        Transform rLegT = new Transform();
        rLegT.setIdentity();

        rLegT.origin.set(rLegPos);
        rLegT.setRotation(qq);
        RigidBody rLegBody = new RigidBody(makeInfo(6, rLegT, lLegShape));
        rLegBody.setLinearVelocity(motion);
        rLegBody.setCcdMotionThreshold(0.01f);
        rLegBody.setDamping(0.05f, 0.85f);
        rLegBody.setCcdSweptSphereRadius(0.35f);
        world.addRigidBody(rLegBody);
        ragdollParts.add(rLegBody);

        // --- Left Arm ---
        CollisionShape lArmShape = new BoxShape(new Vector3f(0.1f, 0.35f, 0.1f));
        Vector3f lArmLocal = new Vector3f(-0.4f, 0.05f, 0f);
        Vector3f lArmPos = worldOffset.apply(lArmLocal);
        Transform lArmT = new Transform();
        lArmT.setIdentity();

        lArmT.origin.set(lArmPos);
        lArmT.setRotation(qq);
        RigidBody lArmBody = new RigidBody(makeInfo(4, lArmT, lArmShape));
        lArmBody.setLinearVelocity(motion);
        lArmBody.applyDamping(0.1f);
        lArmBody.setCcdMotionThreshold(0.01f);
        lArmBody.setDamping(0.05f, 0.85f);
        lArmBody.setCcdSweptSphereRadius(0.3f);
        world.addRigidBody(lArmBody);
        ragdollParts.add(lArmBody);

        // --- Right Arm ---
        Vector3f rArmLocal = new Vector3f(0.4f, 0.05f, 0f);
        Vector3f rArmPos = worldOffset.apply(rArmLocal);
        Transform rArmT = new Transform();
        rArmT.setIdentity();

        rArmT.origin.set(rArmPos);
        rArmT.setRotation(qq);
        RigidBody rArmBody = new RigidBody(makeInfo(4, rArmT, lArmShape));
        rArmBody.setLinearVelocity(motion);
        rArmBody.applyDamping(0.1f);
        rArmBody.setCcdMotionThreshold(0.01f);
        rArmBody.setDamping(0.05f, 0.85f);
        rArmBody.setCcdSweptSphereRadius(0.3f);
        world.addRigidBody(rArmBody);
        ragdollParts.add(rArmBody);

        createRagdollJoints();

    }
    public void applyRandomVelocity(float maxX,float maxY,float maxZ) {
        java.util.Random rand = new java.util.Random();

        for (RigidBody body : ragdollParts) {
            // Random direction and magnitude
            Vector3f impulse = new Vector3f(
                    (rand.nextFloat() - 0.5f) * maxX,  // X impulse ±4
                    (rand.nextFloat() * 5f) + maxY,   // Y impulse upward 2–7
                    (rand.nextFloat() - 0.5f) * maxZ  // Z impulse ±4
            );

            body.applyCentralImpulse(impulse);
        }
    }
    public void applyRandomTorque(float maxX,float maxY,float maxZ) {
        java.util.Random rand = new java.util.Random();
        for (RigidBody body : ragdollParts) {
            Vector3f torque = new Vector3f(
                    (rand.nextFloat() - 0.5f) * maxX, // Random spin
                    (rand.nextFloat() - 0.5f) * maxY,
                    (rand.nextFloat() - 0.5f) * maxZ
            );

            body.applyTorqueImpulse(torque);
        }
    }

    public void applyVel(RagdollPart part,Vec3 vel){
        Vector3f add = new Vector3f((float) vel.x, (float) vel.y, (float) vel.z);
        RigidBody body = ragdollParts.get(part.index);
        body.applyCentralImpulse(add);
    }
    public void applyTorque(RagdollPart part,Vec3 vel){
        Vector3f add = new Vector3f((float) vel.x, (float) vel.y, (float) vel.z);
        RigidBody body = ragdollParts.get(part.index);
        body.applyTorque(add);
    }
    private void createRagdollJoints() {
        if (ragdollParts.size() < 6) return;

        RigidBody torso = ragdollParts.get(RagdollPart.TORSO.index);
        RigidBody head = ragdollParts.get(RagdollPart.HEAD.index);
        RigidBody lLeg = ragdollParts.get(RagdollPart.LEFT_LEG.index);
        RigidBody rLeg = ragdollParts.get(RagdollPart.RIGHT_LEG.index);
        RigidBody lArm = ragdollParts.get(RagdollPart.LEFT_ARM.index);
        RigidBody rArm = ragdollParts.get(RagdollPart.RIGHT_ARM.index);

        // get transforms so we can compute world anchor positions
        Transform tTorso = new Transform(); torso.getMotionState().getWorldTransform(tTorso);
        Transform tHead  = new Transform(); head.getMotionState().getWorldTransform(tHead);
        Transform tLLeg  = new Transform(); lLeg.getMotionState().getWorldTransform(tLLeg);
        Transform tRLeg  = new Transform(); rLeg.getMotionState().getWorldTransform(tRLeg);
        Transform tLArm  = new Transform(); lArm.getMotionState().getWorldTransform(tLArm);
        Transform tRArm  = new Transform(); rArm.getMotionState().getWorldTransform(tRArm);

        // helper to get a torso-space point rotated into world pos
        java.util.function.Function<Vector3f, Vector3f> torsoLocalToWorld = (local) -> {
            Quat4f trot = tTorso.getRotation(new Quat4f());
            Vector3f out = rotateVecByQuat(trot, local);
            out.add(tTorso.origin);
            return out;
        };

        // --- Head <-> Torso ---
        // Torso top & head bottom, anchor = midpoint for stability
        Vector3f torsoTopWorld = torsoLocalToWorld.apply(new Vector3f(0f, 0.4f, 0f));
        // head bottom in head space: (0, -0.2, 0)
        Quat4f hrot = tHead.getRotation(new Quat4f());
        Vector3f headBottomWorld = rotateVecByQuat(hrot, new Vector3f(0f, -0.2f, 0f));
        headBottomWorld.add(tHead.origin);
        Vector3f headAnchor = new Vector3f((torsoTopWorld.x + headBottomWorld.x) * 0.5f,
                (torsoTopWorld.y + headBottomWorld.y) * 0.5f,
                (torsoTopWorld.z + headBottomWorld.z) * 0.5f);
        ragdollJoints.add(createJointAtWorldAnchor(
                torso, head, headAnchor,
                new Vector3f(0,0,0), new Vector3f(0,0,0), // no linear motion
                new Vector3f((float)-Math.toRadians(30), (float)-Math.toRadians(20), (float)-Math.toRadians(30)),
                new Vector3f((float)Math.toRadians(30), (float)Math.toRadians(50), (float)Math.toRadians(30))
        ));

        // --- Left Leg <-> Torso ---
        Vector3f torsoLeftHip = torsoLocalToWorld.apply(new Vector3f(-0.1f, -0.55f, 0f));
        Quat4f lrot = tLLeg.getRotation(new Quat4f());
        Vector3f legTopWorld = rotateVecByQuat(lrot, new Vector3f(0f, 0.45f, 0f));
        legTopWorld.add(tLLeg.origin);
        Vector3f lLegAnchor = new Vector3f((torsoLeftHip.x + legTopWorld.x) * 0.5f,
                (torsoLeftHip.y + legTopWorld.y) * 0.5f,
                (torsoLeftHip.z + legTopWorld.z) * 0.5f);
        ragdollJoints.add(createJointAtWorldAnchor(
                torso, lLeg, lLegAnchor,
                new Vector3f((float)-0.05,(float)-0.05,(float)-0.05), new Vector3f((float)0.05,(float)0.05,(float)0.05),
                new Vector3f((float)-Math.toRadians(40), 0f, (float)-Math.toRadians(10)),
                new Vector3f((float)Math.toRadians(80), 0f, (float)Math.toRadians(10))
        ));

        // --- Right Leg <-> Torso ---
        Vector3f torsoRightHip = torsoLocalToWorld.apply(new Vector3f(0.1f, -0.55f, 0f));
        Quat4f rrot = tRLeg.getRotation(new Quat4f());
        Vector3f rLegTopWorld = rotateVecByQuat(rrot, new Vector3f(0f, 0.45f, 0f));
        rLegTopWorld.add(tRLeg.origin);
        Vector3f rLegAnchor = new Vector3f((torsoRightHip.x + rLegTopWorld.x) * 0.5f,
                (torsoRightHip.y + rLegTopWorld.y) * 0.5f,
                (torsoRightHip.z + rLegTopWorld.z) * 0.5f);
        ragdollJoints.add(createJointAtWorldAnchor(
                torso, rLeg, rLegAnchor,
                new Vector3f((float)-0.05,(float)-0.05,(float)-0.05), new Vector3f((float)0.05,(float)0.05,(float)0.05),
                new Vector3f((float)-Math.toRadians(40), 0f, (float)-Math.toRadians(10)),
                new Vector3f((float)Math.toRadians(80), 0f, (float)Math.toRadians(10))
        ));

        // --- Left Arm <-> Torso ---
        Vector3f torsoLeftShoulder = torsoLocalToWorld.apply(new Vector3f(-0.35f, 0.05f, 0f));
        Quat4f larot = tLArm.getRotation(new Quat4f());
        Vector3f lArmTopWorld = rotateVecByQuat(larot, new Vector3f(0f, 0.35f, 0f));
        lArmTopWorld.add(tLArm.origin);
        Vector3f lArmAnchor = new Vector3f((torsoLeftShoulder.x + lArmTopWorld.x) * 0.5f,
                (torsoLeftShoulder.y + lArmTopWorld.y) * 0.5f,
                (torsoLeftShoulder.z + lArmTopWorld.z) * 0.5f);
        ragdollJoints.add(createJointAtWorldAnchor(
                torso, lArm, lArmAnchor,
                new Vector3f((float)-0.02,(float)-0.02,(float)-0.02), new Vector3f((float)0.02,(float)0.02,(float)0.02),
                new Vector3f((float)-Math.toRadians(80), (float)-Math.toRadians(30), (float)-Math.toRadians(40)),
                new Vector3f((float)Math.toRadians(80), (float)Math.toRadians(30), (float)Math.toRadians(40))
        ));

        // --- Right Arm <-> Torso ---
        Vector3f torsoRightShoulder = torsoLocalToWorld.apply(new Vector3f(0.35f, 0.05f, 0f));
        Quat4f rarot = tRArm.getRotation(new Quat4f());
        Vector3f rArmTopWorld = rotateVecByQuat(rarot, new Vector3f(0f, 0.35f, 0f));
        rArmTopWorld.add(tRArm.origin);
        Vector3f rArmAnchor = new Vector3f((torsoRightShoulder.x + rArmTopWorld.x) * 0.5f,
                (torsoRightShoulder.y + rArmTopWorld.y) * 0.5f,
                (torsoRightShoulder.z + rArmTopWorld.z) * 0.5f);
        ragdollJoints.add(createJointAtWorldAnchor(
                torso, rArm, rArmAnchor,
                new Vector3f((float)-0.02,(float)-0.02,(float)-0.02), new Vector3f((float)0.02,(float)0.02,(float)0.02),
                new Vector3f((float)-Math.toRadians(80), (float)-Math.toRadians(30), (float)-Math.toRadians(40)),
                new Vector3f((float)Math.toRadians(80), (float)Math.toRadians(30), (float)Math.toRadians(40))
        ));
    }
    private Generic6DofConstraint createJoint(
            RigidBody a,
            RigidBody b,
            Vector3f pivotA,
            Vector3f pivotB,
            Vector3f angularLower,
            Vector3f angularUpper
    ) {
        Transform localA = new Transform();
        localA.setIdentity();
        localA.origin.set(pivotA);

        Transform localB = new Transform();
        localB.setIdentity();
        localB.origin.set(pivotB);

        Generic6DofConstraint joint = new Generic6DofConstraint(a, b, localA, localB, true);

        // Keep limbs close but slightly flexible
        joint.setLinearLowerLimit(new Vector3f(-0.1f, -0.1f ,-0.1f));
        joint.setLinearUpperLimit(new Vector3f(0.1f, 0.1f, 0.1f));
        // Apply provided angular constraints
        joint.setAngularLowerLimit(angularLower);
        joint.setAngularUpperLimit(angularUpper);
        joint.getTranslationalLimitMotor().damping = 0.9f;
        world.addConstraint(joint, false);
        return joint;
    }
    public void forcePrecise(Vector3f linearVelocity, Vector3f angularVelocity) {
        if (mode != Mode.PRECISE) {
            setMode(Mode.PRECISE); // your existing method toggles things
        }
        // apply velocities to core parts (e.g., torso)
        if (!ragdollParts.isEmpty()) {
            RigidBody torso = ragdollParts.get(0); // torso as root
            torso.setLinearVelocity(linearVelocity);
            torso.setAngularVelocity(angularVelocity);
        }
    }


    private void checkCollisionsForParts() {
        ServerPlayer player =getPlayerSafe();
        if (player==null)return;
        // Inspect all contact manifolds from dispatcher to find collisions involving our ragdoll parts.
        // For each manifold, examine the bodies and dispatch onPartHit if one is our ragdoll part.
        int numManifolds = manager.getDispatcher().getNumManifolds();
        for (int i = 0; i < numManifolds; i++) {
            PersistentManifold manifold = manager.getDispatcher().getManifoldByIndexInternal(i);
            CollisionObject a = (CollisionObject) manifold.getBody0();
            CollisionObject b = (CollisionObject) manifold.getBody1();
            if (ragdollParts.contains(a) && ragdollParts.contains(b)) continue;
            if (silentParts.contains(a) && silentParts.contains(b)) continue;

            for (int p = 0; p < manifold.getNumContacts(); p++) {
                ManifoldPoint pt = manifold.getContactPoint(p);
                if (pt.getDistance() <= 0f) {
                    // collision happened
                    RigidBody hitPart = null;
                    CollisionObject other = null;
                    if (mode == Mode.PRECISE){
                        if (ragdollParts.contains(a)) { hitPart = (RigidBody) a; other = b; }
                        else if (ragdollParts.contains(b)) { hitPart = (RigidBody) b; other = a; }
                        if (ignoreNext){
                            ignoreNext=false;
                            return;
                        }
                        if (hitPart != null) {
                            Vector3f contactPoint = new Vector3f();
                            pt.getPositionWorldOnB(contactPoint);
                            Vector3f contactLocal = new Vector3f();
                            pt.getPositionWorldOnA(contactLocal);
                            float impactSpeed = computeImpactSpeed(a,b,pt); // rough proxy; you can compute relative velocity
                            dispatchOnPartHit(player, hitPart, other, contactPoint,contactLocal, impactSpeed);
                        }
                    }else{
                        if (silentParts.contains(a)) { hitPart = (RigidBody) a; other = b; }
                        else if (silentParts.contains(b)) { hitPart = (RigidBody) b; other = a; }
                        if (hitPart != null) {
                            Vector3f contactPoint = new Vector3f();
                            pt.getPositionWorldOnB(contactPoint);
                            Vector3f contactLocal = new Vector3f();
                            pt.getPositionWorldOnA(contactLocal);
                            float impactSpeed = computeImpactSpeed(a,b,pt); // rough proxy; you can compute relative velocity
                            dispatchOnPartHit(player, hitPart, other, contactPoint,contactLocal, impactSpeed);
                        }
                    }
                }
            }
        }
    }

    private void dispatchOnPartHit(ServerPlayer player, RigidBody part, CollisionObject other, Vector3f point, Vector3f local, float impact) {
        RagdollPart partName = identifyPart(part); // map body -> "head"/"torso" etc
        // Fire your mod event system or call handlers
        if (impact<0.05)return;
        ModEvents.onPlayerPartHit(player,mode, partName, other, point,local, impact);
    }

    public void destroy() {
        // cleanup bodies & collision objects
        for (RigidBody r : silentParts)world.removeRigidBody(r);
        for (RigidBody r : ragdollParts) world.removeRigidBody(r);
        for (TypedConstraint c : ragdollJoints) world.removeConstraint(c);
        for (CollisionObject co : localStaticCollision) world.removeCollisionObject(co);
    }

    private RagdollTransform[] getRagdollTransforms() {
        // Convert each ragdoll part transform into a lightweight serializable form
        RagdollTransform[] out = new RagdollTransform[ragdollParts.size()];
        for (int i = 0; i < ragdollParts.size(); i++) {
            Transform t = new Transform();
            ragdollParts.get(i).getMotionState().getWorldTransform(t);
            out[i] = new RagdollTransform(i, t.origin.x, t.origin.y, t.origin.z, t.getRotation(new Quat4f()).x, t.getRotation(new Quat4f()).y, t.getRotation(new Quat4f()).z, t.getRotation(new Quat4f()).w);
        }
        return out;
    }
    private BlockPos lastCollisionCenter = BlockPos.ZERO;

    public void updateLocalWorldCollision() {
        ServerPlayer player = getPlayerSafe();
        if (player==null)return;
        BlockPos center = player.getOnPos();
        if (center.distManhattan(lastCollisionCenter) < 2) return; // skip small moves
        lastCollisionCenter = center;

        // Clean up previous collision shapes
        for (CollisionObject c : localStaticCollision) world.removeCollisionObject(c);
        localStaticCollision.clear();

        int radius = 3;

        for (int dx=-radius; dx<=radius; dx++)
            for (int dy=-radius; dy<=radius; dy++)
                for (int dz=-radius; dz<=radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = player.level().getBlockState(pos);
                    if (state.isAir() || state.getFluidState().isSource()) continue;

                    if (isCompletelySurrounded(pos)) continue;
                    VoxelShape shape = state.getCollisionShape(player.level(), pos);
                    if (shape.isEmpty()) continue;

                    for (AABB box : shape.toAabbs()) {
                        Vector3f halfExtents = new Vector3f(
                                (float)(box.getXsize()/2),
                                (float)(box.getYsize()/2),
                                (float)(box.getZsize()/2)
                        );
                        CollisionShape cs = new BoxShape(halfExtents);

                        Transform t = new Transform();
                        t.setIdentity();
                        t.origin.set(
                                (float)(pos.getX() + box.minX + box.getXsize()/2),
                                (float)(pos.getY() + box.minY + box.getYsize()/2),
                                (float)(pos.getZ() + box.minZ + box.getZsize()/2)
                        );

                        RigidBody rb = new RigidBody(new RigidBodyConstructionInfo(0f, new DefaultMotionState(t), cs, new Vector3f()));
                        rb.setCollisionFlags(rb.getCollisionFlags() | CollisionFlags.STATIC_OBJECT);
                        world.addRigidBody(rb);
                        localStaticCollision.add(rb);
                    }
                }
    }

    private boolean isCompletelySurrounded(BlockPos pos) {
        ServerPlayer player = getPlayerSafe();
        if (player==null)return true;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockState neighborState = player.level().getBlockState(neighbor);

            // If any neighbor is air, water, or passable, not surrounded
            if (neighborState.isAir() ||
                    neighborState.getFluidState().isSource() ||
                    neighborState.getCollisionShape(player.level(), neighbor).isEmpty() ||
                    neighborState.canBeReplaced()) {
                return false;
            }
        }
        return true; // All neighbors are solid
    }

    private float computeImpactSpeed(CollisionObject aObj, CollisionObject bObj, ManifoldPoint pt) {
        // Contact position in world coords (use either getPositionWorldOnA/B)
        Vector3f contact = new Vector3f();
        pt.getPositionWorldOnB(contact); // world-space contact point

        Vector3f velA = new Vector3f(0f, 0f, 0f);
        Vector3f velB = new Vector3f(0f, 0f, 0f);
        if (pt.getDistance() < -0.1f) {
            // Contact point is deeply inside another collider → skip
            return 0f;
        }

        if (aObj instanceof RigidBody) {
            RigidBody a = (RigidBody) aObj;
            a.getLinearVelocity(velA); // linear vel of COM
            Vector3f angA = new Vector3f();
            a.getAngularVelocity(angA);

            // compute r = contact - COMpos
            Transform ta = new Transform();
            a.getMotionState().getWorldTransform(ta);
            Vector3f comA = new Vector3f(ta.origin);
            Vector3f rA = new Vector3f();
            rA.sub(contact, comA);

            // v_contact = v_com + w x r
            Vector3f wCrossR = cross(angA, rA);
            velA.add(wCrossR);
        }

        if (bObj instanceof RigidBody) {
            RigidBody b = (RigidBody) bObj;
            b.getLinearVelocity(velB);
            Vector3f angB = new Vector3f();
            b.getAngularVelocity(angB);

            Transform tb = new Transform();
            b.getMotionState().getWorldTransform(tb);
            Vector3f comB = new Vector3f(tb.origin);
            Vector3f rB = new Vector3f();
            rB.sub(contact, comB);

            Vector3f wCrossR = cross(angB, rB);
            velB.add(wCrossR);
        }

        // relative velocity of A w.r.t B at the contact point
        Vector3f rel = new Vector3f();
        rel.sub(velA, velB);
        //if (rel.length()>15) return rel.length()/2;
        return rel.length(); // impact speed (magnitude)
    }

    // small helper for cross product -> returns new Vector3f (not in-place)
    private Vector3f cross(Vector3f a, Vector3f b) {
        Vector3f out = new Vector3f();
        out.x = a.y * b.z - a.z * b.y;
        out.y = a.z * b.x - a.x * b.z;
        out.z = a.x * b.y - a.y * b.x;
        return out;
    }

    public RagdollPart identifyPart(RigidBody rb) {
        int idx = ragdollParts.indexOf(rb);
        RagdollPart part = RagdollPart.byIndex(idx);
        return part;
    }

    public boolean raycastAndInteract(Vec3 from, Vec3 to,Player source) {
        if (mode != Mode.PRECISE || ragdollParts.isEmpty()) return false;

        float nearest = Float.MAX_VALUE;
        RagdollPart hitPart = null;
        Vector3f hitPoint = new Vector3f();

        for (int i = 0; i < ragdollParts.size(); i++) {
            RigidBody body = ragdollParts.get(i);
            Transform t = new Transform();
            body.getMotionState().getWorldTransform(t);

            // Get shape and do a ray test
            CollisionShape shape = body.getCollisionShape();
            Vector3f hit = new Vector3f();
            Vector3f halfExtents = new Vector3f();
            ((BoxShape)shape).getHalfExtentsWithoutMargin(halfExtents);
            if (PhysucsRayUtil.intersectRayAABB(from, to, t.origin, halfExtents, hit)) {
                float dist = (float) from.distanceTo(new Vec3(hit.x, hit.y, hit.z));
                if (dist < nearest) {
                    nearest = dist;
                    hitPart = RagdollPart.byIndex(i);
                    hitPoint.set(hit);
                }
            }
        }

        return false;
    }

    public void applyExplosionImpulse(Vec3 explosionPos, float strength) {
        for (RigidBody body : (mode == Mode.PRECISE ? ragdollParts : silentParts)) {
            Transform t = new Transform();
            body.getMotionState().getWorldTransform(t);
            Vector3f dir = new Vector3f(
                    t.origin.x - (float) explosionPos.x,
                    t.origin.y - (float) explosionPos.y,
                    t.origin.z - (float) explosionPos.z
            );
            float dist = dir.length() + 0.001f;
            dir.normalize();

            float falloff = Mth.clamp(1f - (dist / 6f), 0f, 1f); // 6 blocks radius fade
            float impulseMag = strength * falloff * 20f; // amplify to Bullet scale
            Vector3f impulse = new Vector3f(dir);
            impulse.scale(impulseMag);

            body.applyCentralImpulse(impulse);
        }
    }

    private void applyFluidForces() {
        ServerPlayer player = getPlayerSafe();
        if (player==null)return;
        if (player.isInWater()) {
            Vec3 flow = player.level().getFluidState(player.blockPosition()).getFlow(player.level(), player.blockPosition());
            Vector3f waterFlow = new Vector3f((float) flow.x, (float) flow.y, (float) flow.z);
            waterFlow.scale(5f); // scale up flow for Bullet units

            for (RigidBody body :  ragdollParts ) {
                // drag force = -velocity * damping
                Vector3f vel = new Vector3f();
                body.getLinearVelocity(vel);

                Vector3f drag = new Vector3f(vel);
                drag.scale(-2.7f); // water resistance

                Vector3f net = new Vector3f(waterFlow);
                net.add(drag);

                body.applyCentralImpulse(net);
            }
        }
    }

    public boolean hasBody(CollisionObject obj) {
        return ragdollParts.contains(obj) || silentParts.contains(obj);
    }

    public ServerPlayer getPlayer() {
        return getPlayerSafe();
    }







    // --- helper functions (put them in the same class) ---

    /** Rotate vector v by quaternion q (q assumed normalized). Returns result (new Vector3f). */
    private Vector3f rotateVecByQuat(Quat4f q, Vector3f v) {
        // quaternion rotate: v' = v + 2.0 * cross(q.xyz, cross(q.xyz, v) + q.w * v)
        Vector3f qvec = new Vector3f(q.x, q.y, q.z);

        // t = 2 * cross(qvec, v)
        Vector3f t = new Vector3f();
        t.x = 2f * (qvec.y * v.z - qvec.z * v.y);
        t.y = 2f * (qvec.z * v.x - qvec.x * v.z);
        t.z = 2f * (qvec.x * v.y - qvec.y * v.x);

        // v' = v + q.w * t + cross(qvec, t)
        Vector3f result = new Vector3f(v);
        Vector3f qwt = new Vector3f(t);
        qwt.scale(q.w);
        result.add(qwt);

        Vector3f cross = new Vector3f();
        cross.x = qvec.y * t.z - qvec.z * t.y;
        cross.y = qvec.z * t.x - qvec.x * t.z;
        cross.z = qvec.x * t.y - qvec.y * t.x;
        result.add(cross);

        return result;
    }

    /** Rotate vector v by the conjugate of q (i.e. inverse rotation) */
    private Vector3f rotateVecByQuatConjugate(Quat4f q, Vector3f v) {
        // conjugate of q is (-x, -y, -z, w)
        Quat4f qc = new Quat4f(-q.x, -q.y, -q.z, q.w);
        return rotateVecByQuat(qc, v);
    }

    /** Convert a world-space point into the local coordinates of a body's transform */
    private Vector3f worldPointToLocal(Transform bodyWorldTransform, Vector3f worldPoint) {
        // delta = worldPoint - bodyOrigin
        Vector3f delta = new Vector3f(worldPoint);
        delta.sub(bodyWorldTransform.origin);

        // rotate delta by inverse rotation of the body (conjugate)
        Quat4f rot = bodyWorldTransform.getRotation(new Quat4f());
        return rotateVecByQuatConjugate(rot, delta);
    }

    /** Build a Generic6DofConstraint from a single world anchor point.  */
    private Generic6DofConstraint createJointAtWorldAnchor(RigidBody a, RigidBody b, Vector3f worldAnchor,
                                                           Vector3f linearLower, Vector3f linearUpper,
                                                           Vector3f angularLower, Vector3f angularUpper) {
        // read current body world transforms
        Transform ta = new Transform();
        a.getMotionState().getWorldTransform(ta);
        Transform tb = new Transform();
        b.getMotionState().getWorldTransform(tb);

        // compute local origins for each body's frame (the anchor point in each local space)
        Vector3f localA_origin = worldPointToLocal(ta, worldAnchor);
        Vector3f localB_origin = worldPointToLocal(tb, worldAnchor);

        Transform localA = new Transform();
        localA.setIdentity();
        localA.origin.set(localA_origin);

        Transform localB = new Transform();
        localB.setIdentity();
        localB.origin.set(localB_origin);

        Generic6DofConstraint joint = new Generic6DofConstraint(a, b, localA, localB, true);

        // Apply limits
        joint.setLinearLowerLimit(linearLower);
        joint.setLinearUpperLimit(linearUpper);
        joint.setAngularLowerLimit(angularLower);
        joint.setAngularUpperLimit(angularUpper);

        // Prevent immediate sleeping and ensure it's active
        a.activate();
        b.activate();

        world.addConstraint(joint, true);
        return joint;
    }

    public void setWeight(float value,RagdollPart part){
        RigidBody b = ragdollParts.get(part.index);
        CollisionShape shape = b.getCollisionShape();
        Vector3f inertia = new Vector3f();
        value = Math.max(0,value);
        if (value > 0f) {
            // Properly compute inertia based on shape and mass
            shape.calculateLocalInertia(value, inertia);
        } else {
            inertia.set(0, 0, 0);
        }
        b.setMassProps(value,inertia);
    }

    public Quaternionf getRotation(RagdollPart part){
        RigidBody b = ragdollParts.get(part.index);
        Transform t = new Transform();
        b.getWorldTransform(t);
        Quat4f q = new Quat4f();
        t.getRotation(q);
        return new Quaternionf(q.x,q.y,q.z,q.w);
    }

    public Vec3 getVelocity(RagdollPart part){
        RigidBody b = ragdollParts.get(part.index);
        Vector3f vector3f = new Vector3f();
        b.getAngularVelocity(vector3f);
        return new Vec3(vector3f.x,vector3f.y,vector3f.z);
    }

    private void correctInterpenetrations() {
        for (PersistentManifold manifold : manager.getDispatcher().getInternalManifoldPointer()) {
            int numContacts = manifold.getNumContacts();
            for (int i = 0; i < numContacts; i++) {
                ManifoldPoint point = manifold.getContactPoint(i);
                if (point.getDistance() < -0.1f) { // deep penetration
                    RigidBody a = (RigidBody) manifold.getBody0();
                    RigidBody b = (RigidBody) manifold.getBody1();
                    PrototypePhysics.LOGGER.info("INTERSECTED");
                    ignoreNext = true;

                    Vector3f normal = point.normalWorldOnB;
                    normal.scale(4.5f * -point.getDistance()); // half the depth
                    if (a.getInvMass() > 0) a.translate(normal);
                    normal.scale(-1);
                    if (b.getInvMass() > 0) b.translate(normal);
                }
            }
        }
    }

    private Vec3 findNearestAirPosition(ServerPlayer player, Vector3d start) {
        Level level = player.level();
        BlockPos.MutableBlockPos test = new BlockPos.MutableBlockPos(start.x, start.y, start.z);

        for (int i = 0; i < 10; i++) {
            if (level.getBlockState(test).isAir()) {
                return new Vec3(test.getX() + 0.5, test.getY() + 0.5, test.getZ() + 0.5);
            }
            test.move(Direction.UP);
        }
        return new Vec3(start.x, start.y + 1.0, start.z); // fallback
    }

}