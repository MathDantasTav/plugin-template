package dev.mathantastic.betterai.npc;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.flock.FlockMembership;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.BodyMotionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.movement.Steering;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerFly;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.IPositionProvider;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Flies with the flock (see BirdFlightRegistry): once around in a circle, then far out in one direction,
 * then lands and switches to the Walk controller. With FleeFromTarget the flock heads straight away from the sensed target.
 */
public class BodyMotionBirdFlight extends BodyMotionBase {

    private static final double[] LANDING_ALTITUDE = {0.0, 0.5};
    private static final double TOUCHDOWN_HEIGHT = 1.5;
    private static final double LANDING_TIMEOUT = 20.0;
    private static final double[] FREE_ALTITUDE = {0.0, 512.0}; // our own altitude logic decides, not the controller's
    private static final double RISE_EASE = 1.0; // how fast the target height follows the terrain, per second
    private static final double SINK_EASE = 0.3;
    private static final double CLIMB_RESPONSE = 6.0; // blocks of height error for full climb
    private static final double MAX_CLIMB = 0.5;
    private static final double MAX_CRUISE_SINK = 0.3;
    private static final double CLEARANCE = 4.0; // closer than this to the ground or a tree and we climb right away
    private static final double CLEARANCE_CLIMB = 0.7;
    private static final double PROBE_INTERVAL = 0.25;
    private static final int SCAN_UP = 48;
    private static final int SCAN_DOWN = 64;
    private static final int LOOK_AHEAD_STEPS = 5;
    private static final double LOOK_AHEAD_SPACING = 7.0;
    private static final double NEAR_DISTANCE = 8.0;
    private static final double TREE_EXTRA_DISTANCE = 15.0;
    private static final double GLIDE_SINK_HEIGHT = 8.0; // sink slows down below this height
    private static final double MIN_SINK = 0.08;
    private static final double MAX_SINK = 0.4;
    private static final double STUCK_SPEED = 0.5; // blocks per second; slower than this while landing counts as stuck
    private static final double STUCK_DROP = 0.3; // stuck this long: drop straight down
    private static final double STUCK_TOUCHDOWN = 1.5; // still stuck: hand over to Walk and fall

    protected final BirdFlightRegistry.Settings settings;
    protected final double[] cruiseAltitude;

    protected final Vector3d threat = new Vector3d();
    protected final double[] out = new double[BirdFlightRegistry.OUT_SIZE];
    protected double lastDirX = 1.0;
    protected double lastDirZ = 0.0;
    protected double landingTime;
    protected double easedY = Double.NaN;
    protected double probeTimer;
    protected int groundBelow = TerrainProbe.NO_GROUND;
    protected int groundNear = TerrainProbe.NO_GROUND;
    protected int groundAhead = TerrainProbe.NO_GROUND;
    protected boolean treeBelow;
    protected boolean dropping;
    protected double stuckTime;
    protected final Vector3d lastPos = new Vector3d();
    protected boolean hasLastPos;

    public BodyMotionBirdFlight(BuilderBodyMotionBirdFlight builder, BuilderSupport support) {
        super(builder);
        double minDistance = builder.getFlightDistanceMin(support);
        this.settings = new BirdFlightRegistry.Settings(
                builder.getRelativeSpeed(support),
                minDistance,
                Math.max(minDistance, builder.getFlightDistanceMax(support)),
                builder.getCircleRadius(support),
                builder.getSeparationDistance(support),
                builder.isFleeFromTarget(support));
        double cruiseMin = builder.getCruiseHeightMin(support);
        this.cruiseAltitude = new double[]{cruiseMin, Math.max(cruiseMin, builder.getCruiseHeightMax(support))};
    }

    @Override
    public boolean computeSteering(Ref<EntityStore> ref, ExecutionSupport support, InfoProvider info, double dt,
                                   Steering steering, ComponentAccessor<EntityStore> accessor) {
        steering.clear();
        MotionController controller = support.getMotionContextSupport().getActiveMotionController();
        if (!(controller instanceof MotionControllerFly fly)) {
            return false;
        }
        TransformComponent transform = accessor.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        Vector3d pos = transform.getPosition();
        Object flockKey = flockKey(ref, accessor);

        boolean landing = BirdFlightRegistry.update(flockKey, ref, pos, this.threatPosition(info), fly.isObstructed(),
                fly.getMaximumSpeed(), this.settings, this.out);

        double dx = this.out[BirdFlightRegistry.OUT_DIR_X];
        double dz = this.out[BirdFlightRegistry.OUT_DIR_Z];
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-4) {
            // Forces cancelled out: keep going the way we were.
            dx = this.lastDirX;
            dz = this.lastDirZ;
        } else {
            dx /= length;
            dz /= length;
            this.lastDirX = dx;
            this.lastDirZ = dz;
        }
        steering.setYaw(PhysicsMath.headingFromDirection(dx, dz));

        this.probeTerrain(pos, dx, dz, dt, accessor);

        if (!landing) {
            this.resetLanding();
            double speed = this.out[BirdFlightRegistry.OUT_SPEED];
            steering.setTranslation(dx * speed, this.cruiseClimb(fly, pos, dt), dz * speed);
            return true;
        }

        this.landingTime += dt;
        this.trackStuck(pos, dt);
        double height = fly.getHeightOverGround(); // -1 when the ground is out of probe range
        if (fly.onGround() || (height >= 0 && height < TOUCHDOWN_HEIGHT) || this.landingTime > LANDING_TIMEOUT
                || this.stuckTime > STUCK_TOUCHDOWN) {
            // Stuck counts as landed too: Walk has gravity, so it falls the rest of the way.
            this.touchDown(ref, support, fly, accessor, flockKey);
            return true;
        }
        fly.setDesiredAltitudeOverride(LANDING_ALTITUDE);
        if (fly.isObstructed() || this.stuckTime > STUCK_DROP) {
            this.dropping = true;
        }
        if (this.dropping) {
            // Hit something on the way down: drop straight down.
            steering.setTranslation(0.0, -MAX_SINK, 0.0);
            return true;
        }
        if (this.treeBelow) {
            // Don't come down in a tree: keep going a bit and try again.
            BirdFlightRegistry.extend(flockKey, TREE_EXTRA_DISTANCE);
            this.treeBelow = false;
        }
        // Glide in: sink and slow down less and less the closer we get to the ground.
        double above = this.groundBelow == TerrainProbe.NO_GROUND ? GLIDE_SINK_HEIGHT : pos.y - this.groundBelow;
        double sink = Math.max(MIN_SINK, Math.min(MAX_SINK, above / GLIDE_SINK_HEIGHT * MAX_SINK));
        double forward = Math.max(0.15, Math.min(0.45, 0.15 + above / 20.0));
        steering.setTranslation(dx * forward, -sink, dz * forward);
        return true;
    }

    // Counts how long we've barely moved (pinned against a wall).
    private void trackStuck(Vector3d pos, double dt) {
        if (this.hasLastPos && this.lastPos.distance(pos) < STUCK_SPEED * dt) {
            this.stuckTime += dt;
        } else {
            this.stuckTime = 0.0;
        }
        this.lastPos.set(pos);
        this.hasLastPos = true;
    }

    private void resetLanding() {
        this.landingTime = 0.0;
        this.stuckTime = 0.0;
        this.dropping = false;
        this.hasLastPos = false;
    }

    // Samples the ground below and along the flight path a few times a second (tree tops count as ground).
    private void probeTerrain(Vector3d pos, double dx, double dz, double dt, ComponentAccessor<EntityStore> accessor) {
        this.probeTimer -= dt;
        if (this.probeTimer > 0.0) {
            return;
        }
        this.probeTimer = PROBE_INTERVAL;
        ChunkStore chunks = TerrainProbe.chunkStore(accessor);
        int y = (int) Math.floor(pos.y);
        int top = y + SCAN_UP;
        int bottom = y - SCAN_DOWN;
        this.groundBelow = TerrainProbe.groundLevel(chunks, pos.x, pos.z, top, bottom);
        this.groundNear = this.groundBelow;
        this.groundAhead = this.groundBelow;
        for (int i = 1; i <= LOOK_AHEAD_STEPS; i++) {
            double d = i * LOOK_AHEAD_SPACING;
            int ground = TerrainProbe.groundLevel(chunks, pos.x + dx * d, pos.z + dz * d, top, bottom);
            this.groundAhead = Math.max(this.groundAhead, ground);
            if (d <= NEAR_DISTANCE) {
                this.groundNear = Math.max(this.groundNear, ground);
            }
        }
        this.treeBelow = this.groundBelow != TerrainProbe.NO_GROUND
                && TerrainProbe.isLeaves(chunks, pos.x, this.groundBelow - 1, pos.z);
    }

    // Eases towards cruise height over the highest ground ahead: rises early for hills and trees, sinks back slowly.
    private double cruiseClimb(MotionControllerFly fly, Vector3d pos, double dt) {
        fly.setDesiredAltitudeOverride(FREE_ALTITUDE);
        if (this.groundAhead == TerrainProbe.NO_GROUND) {
            return 0.0;
        }
        double goal = this.groundAhead + this.cruiseAltitude[0]
                + (this.cruiseAltitude[1] - this.cruiseAltitude[0]) * this.out[BirdFlightRegistry.OUT_CRUISE_FRACTION]
                + this.out[BirdFlightRegistry.OUT_HEIGHT_OFFSET];
        if (Double.isNaN(this.easedY)) {
            this.easedY = pos.y;
        }
        double ease = goal > this.easedY ? RISE_EASE : SINK_EASE;
        this.easedY += (goal - this.easedY) * Math.min(1.0, ease * dt);
        double climb = Math.max(-MAX_CRUISE_SINK, Math.min(MAX_CLIMB, (this.easedY - pos.y) / CLIMB_RESPONSE));
        if (this.groundNear != TerrainProbe.NO_GROUND && pos.y - this.groundNear < CLEARANCE) {
            climb = Math.max(climb, CLEARANCE_CLIMB);
        }
        return climb;
    }
    @Override
    public void deactivate(Ref<EntityStore> ref, ExecutionSupport support, ComponentAccessor<EntityStore> accessor) {
        MotionController controller = support.getMotionContextSupport().getActiveMotionController();
        if (controller instanceof MotionControllerFly fly) {
            fly.clearOverrides();
        }
        super.deactivate(ref, support, accessor);
    }

    private void touchDown(Ref<EntityStore> ref, ExecutionSupport support, MotionControllerFly fly,
                           ComponentAccessor<EntityStore> accessor, Object flockKey) {
        this.resetLanding();
        this.easedY = Double.NaN;
        fly.clearOverrides();
        BirdFlightRegistry.leave(flockKey, ref);
        Role role = support.getRole();
        NPCEntity npc = accessor.getComponent(ref, NPCEntity.getComponentType());
        if (role != null && npc != null && role.hasMotionController("Walk")) {
            role.setActiveMotionController(ref, npc, "Walk", accessor);
        }
    }

    private Vector3d threatPosition(InfoProvider info) {
        if (!this.settings.flee() || info == null || !info.hasPosition()) {
            return null;
        }
        IPositionProvider provider = info.getPositionProvider();
        return provider != null && provider.providePosition(this.threat) ? this.threat : null;
    }

    private static Object flockKey(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        FlockMembership membership = accessor.getComponent(ref, FlockMembership.getComponentType());
        UUID flockId = membership == null ? null : membership.getFlockId();
        return flockId != null ? flockId : ref;
    }
}
