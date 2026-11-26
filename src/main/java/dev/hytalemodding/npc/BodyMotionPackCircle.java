package dev.hytalemodding.npc;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.util.PhysicsMath;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.BodyMotionBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.movement.Steering;
import com.hypixel.hytale.server.npc.sensorinfo.IPositionProvider;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import org.joml.Vector3d;

/**
 * Circles the sensed target together with every other NPC circling it (see PackCircleRegistry).
 */
public class BodyMotionPackCircle extends BodyMotionBase {

    private static final double ON_SPOT_DISTANCE = 1.5;
    private static final double CATCH_UP_RAMP = 4.0;

    protected final double radius;
    protected final double angularSpeed;
    protected final double relativeSpeed;
    protected final double catchUpRelativeSpeed;
    protected final double leadAngle;
    protected final double radiusJitter;
    protected final double spacingJitter;
    protected final double speedJitter;

    protected final Vector3d targetPosition = new Vector3d();
    protected final double[] circleInfo = new double[3];
    protected Object currentTargetKey;

    public BodyMotionPackCircle(BuilderBodyMotionPackCircle builder, BuilderSupport support) {
        super(builder);
        this.radius = builder.getRadius(support);
        this.angularSpeed = builder.getOrbitSpeed(support) / this.radius;
        this.relativeSpeed = builder.getRelativeSpeed(support);
        this.catchUpRelativeSpeed = builder.getCatchUpRelativeSpeed(support);
        this.leadAngle = this.angularSpeed * builder.getLeadTime(support);
        this.radiusJitter = Math.min(builder.getRadiusJitter(support), this.radius * 0.5); // never closer than half the radius
        this.spacingJitter = Math.toRadians(builder.getSpacingJitter(support));
        this.speedJitter = builder.getSpeedJitter(support);
    }

    @Override
    public boolean computeSteering(Ref<EntityStore> ref, ExecutionSupport support, InfoProvider info, double dt,
                                   Steering steering, ComponentAccessor<EntityStore> accessor) {
        if (info == null || !info.hasPosition()) {
            this.leaveCircle(ref);
            steering.clear();
            return false;
        }
        IPositionProvider positionProvider = info.getPositionProvider();
        if (positionProvider == null || !positionProvider.providePosition(this.targetPosition)) {
            this.leaveCircle(ref);
            steering.clear();
            return false;
        }
        TransformComponent transform = accessor.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            steering.clear();
            return false;
        }
        Vector3d self = transform.getPosition();

        // No target entity: circle on our own.
        Object targetKey = positionProvider.getTarget();
        if (targetKey == null) {
            targetKey = ref;
        }
        if (this.currentTargetKey != null && this.currentTargetKey != targetKey) {
            PackCircleRegistry.leave(this.currentTargetKey, ref);
        }
        this.currentTargetKey = targetKey;

        double currentAngle = Math.atan2(self.z - this.targetPosition.z, self.x - this.targetPosition.x);
        PackCircleRegistry.update(targetKey, ref, currentAngle, this.angularSpeed, this.leadAngle, this.spacingJitter, this.circleInfo);
        double angle = this.circleInfo[PackCircleRegistry.OUT_ANGLE];
        double myRadius = this.radius + this.circleInfo[PackCircleRegistry.OUT_RADIUS_BIAS] * this.radiusJitter;
        double speedScale = 1.0 + this.circleInfo[PackCircleRegistry.OUT_SPEED_BIAS] * this.speedJitter;

        double goalX = this.targetPosition.x + myRadius * Math.cos(angle);
        double goalZ = this.targetPosition.z + myRadius * Math.sin(angle);
        double dx = goalX - self.x;
        double dz = goalZ - self.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0e-3) {
            steering.clear();
            return true;
        }

        double ramp = Math.max(0.0, Math.min(1.0, (distance - ON_SPOT_DISTANCE) / CATCH_UP_RAMP));
        double speed = (this.relativeSpeed + (this.catchUpRelativeSpeed - this.relativeSpeed) * ramp) * speedScale;

        steering.clear();
        steering.setTranslation(dx / distance * speed, 0.0, dz / distance * speed);
        steering.setYaw(PhysicsMath.headingFromDirection(dx, dz));
        return true;
    }

    @Override
    public void deactivate(Ref<EntityStore> ref, ExecutionSupport support, ComponentAccessor<EntityStore> accessor) {
        this.leaveCircle(ref);
        super.deactivate(ref, support, accessor);
    }

    private void leaveCircle(Ref<EntityStore> ref) {
        if (this.currentTargetKey != null) {
            PackCircleRegistry.leave(this.currentTargetKey, ref);
            this.currentTargetKey = null;
        }
    }
}
