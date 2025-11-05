package net.adinvas.prototype_physics;

import com.bulletphysics.collision.dispatch.CollisionFlags;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.narrowphase.ManifoldPoint;
import com.bulletphysics.collision.narrowphase.PersistentManifold;
import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.dynamics.constraintsolver.TypedConstraint;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import net.adinvas.prototype_physics.events.ModEvents;
import net.adinvas.prototype_physics.network.ModNetwork;
import net.adinvas.prototype_physics.network.RagdollEndPacket;
import net.adinvas.prototype_physics.network.RagdollStartPacket;
import net.adinvas.prototype_physics.network.RagdollUpdatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class PlayerPhysics {
    public enum Mode { SILENT, PRECISE }

    private final ServerPlayer player;
    private final JbulletWorld manager;
    private final DiscreteDynamicsWorld world;
    private Mode mode = Mode.SILENT;

    private RigidBody silentUpper, silentLower;

    private final List<RigidBody> ragdollParts = new ArrayList<>(6);
    private final List<TypedConstraint> ragdollJoints = new ArrayList<>(5);

    private final List<CollisionObject> localStaticCollision = new ArrayList<>();

    private final int networkRagdollId;

    public PlayerPhysics(ServerPlayer player, JbulletWorld manager, DiscreteDynamicsWorld world) {
        this.player = player;
        this.manager = manager;
        this.world = world;
        this.networkRagdollId = player.getId(); // simple choice; use a unique id scheme in production
        createSilentBodies();
        updateLocalWorldCollision(); // build initial 5x5x5
    }

    private void createSilentBodies() {
        // Two boxes: lower (legs) and upper (torso/head)
        CollisionShape lowerShape = new BoxShape(new Vector3f(0.25f, 0.5f, 0.25f)); // width, height, depth
        CollisionShape upperShape = new BoxShape(new Vector3f(0.25f, 0.75f, 0.25f));

        Transform t = new Transform();
        t.setIdentity();
            Vector3d pos = new Vector3d(player.getX(),player.getY(),player.getZ()); // pseudocode — convert to Vector3f
        t.origin.set((float)pos.x, (float)pos.y, (float)pos.z);

        RigidBodyConstructionInfo lowerInfo = makeInfo(0f, t, lowerShape);
        silentLower = new RigidBody(lowerInfo);
        silentLower.setCollisionFlags(silentLower.getCollisionFlags() | CollisionFlags.KINEMATIC_OBJECT);
        silentLower.setActivationState(CollisionObject.DISABLE_DEACTIVATION);
        world.addRigidBody(silentLower);

        RigidBodyConstructionInfo upperInfo = makeInfo(0f, t, upperShape);
        silentUpper = new RigidBody(upperInfo);
        silentUpper.setCollisionFlags(silentUpper.getCollisionFlags() | CollisionFlags.KINEMATIC_OBJECT);
        silentUpper.setActivationState(CollisionObject.DISABLE_DEACTIVATION);
        world.addRigidBody(silentUpper);
    }

    private RigidBodyConstructionInfo makeInfo(float mass, Transform t, CollisionShape shape) {
        Vector3f inertia = new Vector3f(0,0,0);
        if (mass > 0f) shape.calculateLocalInertia(mass, inertia);
        DefaultMotionState ms = new DefaultMotionState(t);
        return new RigidBodyConstructionInfo(mass, ms, shape, inertia);
    }

    public void setMode(Mode newMode) {
        if (newMode == this.mode) return;
        if (newMode == Mode.PRECISE) {
            enterRagdollMode();
        } else {
            exitRagdollMode();
        }
        this.mode = newMode;
    }

    public Mode getMode() {
        return mode;
    }

    private void enterRagdollMode() {
        // remove silent bodies
        world.removeRigidBody(silentUpper);
        world.removeRigidBody(silentLower);

        // create ragdoll parts (6 bodies). Example layout: head, torso, pelvis, leftArm, rightArm, legs combined maybe
        // massed bodies
        createRagdollBodies();

        // create joints: e.g. pelvis <-> torso, torso <-> head, torso <-> leftArm, torso <-> rightArm, pelvis <-> legs (or separate)
        createRagdollJoints();

        // inform clients to spawn ragdoll visuals
        ModNetwork.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new RagdollStartPacket(player.getId(), networkRagdollId, getRagdollTransforms())
        );
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
            RagdollTransform[] transforms = getRagdollTransforms();
            ModNetwork.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY.with(() -> player),
                    new RagdollUpdatePacket(networkRagdollId, System.currentTimeMillis(), transforms)
            );
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RagdollUpdatePacket(networkRagdollId, System.currentTimeMillis(), transforms)
            );

            // collision detection: we attach custom ContactResultCallback or poll manifolds
            RigidBody torsoBody = ragdollParts.get(1);

            Transform torsoTransform = new Transform();
            torsoBody.getMotionState().getWorldTransform(torsoTransform);
            Vector3f pos = torsoTransform.origin;
            player.teleportTo(pos.x, pos.y, pos.z);
            checkCollisionsForParts();
            updateLocalWorldCollision();
        }

    }
    private void updateSilentFromPlayer() {
        // set kinematic transforms for silentUpper/silentLower to where the player is (sync)
        Transform t = new Transform();
        t.setIdentity();
        Vector3d ppos = new Vector3d(player.getX(),player.getY(),player.getZ());
        t.origin.set((float)ppos.x, (float)ppos.y, (float)ppos.z);
        silentUpper.getMotionState().setWorldTransform(t);
        silentLower.getMotionState().setWorldTransform(t);
    }

    private void createRagdollBodies() {
        Vec3 plM = player.getDeltaMovement(); // current player velocity
        Vector3f motion = new Vector3f((float) plM.x*20, (float) plM.y*20, (float) plM.z*20);

        Vector3d pos = new Vector3d(player.getX(), player.getY(), player.getZ());

        // --- Pelvis / Torso ---
        CollisionShape torsoShape = new BoxShape(new Vector3f(0.25f, 0.4f, 0.15f));
        Transform torsoT = new Transform();
        torsoT.setIdentity();
        torsoT.origin.set((float) pos.x, (float) pos.y + 1.0f, (float) pos.z); // torso above legs
        RigidBody torsoBody = new RigidBody(makeInfo(8f, torsoT, torsoShape));
        torsoBody.setLinearVelocity(motion);
        world.addRigidBody(torsoBody);
        ragdollParts.add(torsoBody);

        // --- Head ---
        CollisionShape headShape = new BoxShape(new Vector3f(0.2f, 0.2f, 0.2f));
        Transform headT = new Transform();
        headT.setIdentity();
        headT.origin.set((float) pos.x, (float) pos.y + 1.75f, (float) pos.z);
        RigidBody headBody = new RigidBody(makeInfo(3f, headT, headShape));
        headBody.setLinearVelocity(motion);
        world.addRigidBody(headBody);
        ragdollParts.add(headBody);

        // --- Left Leg ---
        CollisionShape lLegShape = new BoxShape(new Vector3f(0.15f, 0.45f, 0.15f));
        Transform lLegT = new Transform();
        lLegT.setIdentity();
        lLegT.origin.set((float) pos.x - 0.2f, (float) pos.y + 0.45f, (float) pos.z);
        RigidBody lLegBody = new RigidBody(makeInfo(6f, lLegT, lLegShape));
        lLegBody.setLinearVelocity(motion);
        world.addRigidBody(lLegBody);
        ragdollParts.add(lLegBody);

        // --- Right Leg ---
        Transform rLegT = new Transform();
        rLegT.setIdentity();
        rLegT.origin.set((float) pos.x + 0.2f, (float) pos.y + 0.45f, (float) pos.z);
        RigidBody rLegBody = new RigidBody(makeInfo(6f, rLegT, lLegShape));
        rLegBody.setLinearVelocity(motion);
        world.addRigidBody(rLegBody);
        ragdollParts.add(rLegBody);

        // --- Left Arm ---
        CollisionShape lArmShape = new BoxShape(new Vector3f(0.1f, 0.35f, 0.1f));
        Transform lArmT = new Transform();
        lArmT.setIdentity();
        lArmT.origin.set((float) pos.x - 0.35f, (float) pos.y + 1.3f, (float) pos.z);
        RigidBody lArmBody = new RigidBody(makeInfo(4f, lArmT, lArmShape));
        lArmBody.setLinearVelocity(motion);
        world.addRigidBody(lArmBody);
        ragdollParts.add(lArmBody);

        // --- Right Arm ---
        Transform rArmT = new Transform();
        rArmT.setIdentity();
        rArmT.origin.set((float) pos.x + 0.35f, (float) pos.y + 1.3f, (float) pos.z);
        RigidBody rArmBody = new RigidBody(makeInfo(4f, rArmT, lArmShape));
        rArmBody.setLinearVelocity(motion);
        world.addRigidBody(rArmBody);
        ragdollParts.add(rArmBody);
    }
    private void applyRandomImpulse() {
        java.util.Random rand = new java.util.Random();

        for (RigidBody body : ragdollParts) {
            // Random direction and magnitude
            Vector3f impulse = new Vector3f(
                    (rand.nextFloat() - 0.5f) * 8f,  // X impulse ±4
                    (rand.nextFloat() * 5f) + 2f,   // Y impulse upward 2–7
                    (rand.nextFloat() - 0.5f) * 8f  // Z impulse ±4
            );

            Vector3f torque = new Vector3f(
                    (rand.nextFloat() - 0.5f) * 30f, // Random spin
                    (rand.nextFloat() - 0.5f) * 30f,
                    (rand.nextFloat() - 0.5f) * 30f
            );

            body.applyCentralImpulse(impulse);
            body.applyTorqueImpulse(torque);
        }
    }
    private void createRagdollJoints() {
        if (ragdollParts.size() < 6) return;

        RigidBody torso = ragdollParts.get(0);
        RigidBody head = ragdollParts.get(1);
        RigidBody lLeg = ragdollParts.get(2);
        RigidBody rLeg = ragdollParts.get(3);
        RigidBody lArm = ragdollParts.get(4);
        RigidBody rArm = ragdollParts.get(5);

        // --- Head <-> Torso ---
        Generic6DofConstraint headJoint = createJoint(torso, head, new Vector3f(0,0.4f,0), new Vector3f(0,-0.2f,0));
        ragdollJoints.add(headJoint);

        // --- Left Leg <-> Torso ---
        Generic6DofConstraint lLegJoint = createJoint(torso, lLeg, new Vector3f(-0.2f,-0.4f,0), new Vector3f(0,0.45f,0));
        ragdollJoints.add(lLegJoint);

        // --- Right Leg <-> Torso ---
        Generic6DofConstraint rLegJoint = createJoint(torso, rLeg, new Vector3f(0.2f,-0.4f,0), new Vector3f(0,0.45f,0));
        ragdollJoints.add(rLegJoint);

        // --- Left Arm <-> Torso ---
        Generic6DofConstraint lArmJoint = createJoint(torso, lArm, new Vector3f(-0.25f,0.2f,0), new Vector3f(0,0.35f,0));
        ragdollJoints.add(lArmJoint);

        // --- Right Arm <-> Torso ---
        Generic6DofConstraint rArmJoint = createJoint(torso, rArm, new Vector3f(0.25f,0.2f,0), new Vector3f(0,0.35f,0));
        ragdollJoints.add(rArmJoint);
    }

    private Generic6DofConstraint createJoint(RigidBody a, RigidBody b, Vector3f pivotA, Vector3f pivotB) {
        Transform localA = new Transform();
        localA.setIdentity();
        localA.origin.set(pivotA);
        Transform localB = new Transform();
        localB.setIdentity();
        localB.origin.set(pivotB);

        Generic6DofConstraint joint = new Generic6DofConstraint(a, b, localA, localB, true);

        // Very loose, near-free movement
        joint.setLinearLowerLimit(new Vector3f(-0.05f, -0.05f, -0.05f));
        joint.setLinearUpperLimit(new Vector3f(0.05f, 0.05f, 0.05f));
        joint.setAngularLowerLimit(new Vector3f((float) -Math.PI, (float) -Math.PI, (float) -Math.PI));
        joint.setAngularUpperLimit(new Vector3f((float) Math.PI, (float) Math.PI, (float) Math.PI));

        world.addConstraint(joint);
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
        // Inspect all contact manifolds from dispatcher to find collisions involving our ragdoll parts.
        // For each manifold, examine the bodies and dispatch onPartHit if one is our ragdoll part.
        int numManifolds = manager.getDispatcher().getNumManifolds();
        for (int i = 0; i < numManifolds; i++) {
            PersistentManifold manifold = manager.getDispatcher().getManifoldByIndexInternal(i);
            CollisionObject a = (CollisionObject) manifold.getBody0();
            CollisionObject b = (CollisionObject) manifold.getBody1();
            for (int p = 0; p < manifold.getNumContacts(); p++) {
                ManifoldPoint pt = manifold.getContactPoint(p);
                if (pt.getDistance() <= 0f) {
                    // collision happened
                    RigidBody hitPart = null;
                    CollisionObject other = null;
                    if (ragdollParts.contains(a)) { hitPart = (RigidBody) a; other = b; }
                    else if (ragdollParts.contains(b)) { hitPart = (RigidBody) b; other = a; }
                    if (hitPart != null) {
                        Vector3f contactPoint = new Vector3f();
                        pt.getPositionWorldOnB(contactPoint);
                        float impactSpeed = computeImpactSpeed(a,b,pt); // rough proxy; you can compute relative velocity
                        dispatchOnPartHit(player, hitPart, other, contactPoint, impactSpeed);
                    }
                }
            }
        }
    }

    private void dispatchOnPartHit(ServerPlayer player, RigidBody part, CollisionObject other, Vector3f point, float impact) {
        String partName = identifyPart(part); // map body -> "head"/"torso" etc
        // Fire your mod event system or call handlers
        ModEvents.onPlayerPartHit(player, partName, other, point, impact);
    }

    public void destroy() {
        // cleanup bodies & collision objects
        if (silentUpper != null) world.removeRigidBody(silentUpper);
        if (silentLower != null) world.removeRigidBody(silentLower);
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
        BlockPos center = player.getOnPos();
        if (center.distManhattan(lastCollisionCenter) < 2) return; // skip small moves
        lastCollisionCenter = center;

        // Clean up previous collision shapes
        for (CollisionObject c : localStaticCollision) world.removeCollisionObject(c);
        localStaticCollision.clear();

        int radius = 2;

        for (int dx=-radius; dx<=radius; dx++)
            for (int dy=-radius; dy<=radius; dy++)
                for (int dz=-radius; dz<=radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = player.level().getBlockState(pos);
                    if (state.isAir() || state.getFluidState().isSource()) continue;

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

    private float computeImpactSpeed(CollisionObject aObj, CollisionObject bObj, ManifoldPoint pt) {
        // Contact position in world coords (use either getPositionWorldOnA/B)
        Vector3f contact = new Vector3f();
        pt.getPositionWorldOnB(contact); // world-space contact point

        Vector3f velA = new Vector3f(0f, 0f, 0f);
        Vector3f velB = new Vector3f(0f, 0f, 0f);

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

    private String identifyPart(RigidBody rb) { /* map index -> name */ return "torso"; }
}
