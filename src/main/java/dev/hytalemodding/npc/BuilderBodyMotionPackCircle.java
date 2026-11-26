package dev.hytalemodding.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.Feature;
import com.hypixel.hytale.server.npc.asset.builder.holder.DoubleHolder;
import com.hypixel.hytale.server.npc.asset.builder.validators.DoubleRangeValidator;
import com.hypixel.hytale.server.npc.asset.builder.validators.DoubleSingleValidator;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderBodyMotionBase;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerWalk;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import com.hypixel.hytale.server.npc.util.expression.Scope;
import com.hypixel.hytale.server.npc.validators.NPCLoadTimeValidationHelper;

import java.util.List;

/**
 * Builder for the "PackCircle" body motion. Needs a sensor that provides a position, like MaintainDistance.
 */
public class BuilderBodyMotionPackCircle extends BuilderBodyMotionBase {

    public static final String TYPE_NAME = "PackCircle";

    protected final DoubleHolder radius = new DoubleHolder();
    protected final DoubleHolder orbitSpeed = new DoubleHolder();
    protected final DoubleHolder relativeSpeed = new DoubleHolder();
    protected final DoubleHolder catchUpRelativeSpeed = new DoubleHolder();
    protected final DoubleHolder leadTime = new DoubleHolder();
    protected final DoubleHolder radiusJitter = new DoubleHolder();
    protected final DoubleHolder spacingJitter = new DoubleHolder();
    protected final DoubleHolder speedJitter = new DoubleHolder();

    @Override
    public BodyMotionPackCircle build(BuilderSupport support) {
        return new BodyMotionPackCircle(this, support);
    }

    @Override
    public String getShortDescription() {
        return "Circle a target together with the rest of the pack";
    }

    @Override
    public String getLongDescription() {
        return "Circles the target in a shared ring that spreads the pack out and turns in one direction.";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Override
    public BuilderBodyMotionPackCircle readConfig(JsonElement data) {
        this.getDouble(data, "Radius", this.radius, 7.5, DoubleSingleValidator.greater0(), BuilderDescriptorState.Stable,
                "Distance to circle at.", null);
        this.getDouble(data, "OrbitSpeed", this.orbitSpeed, 3.0, DoubleSingleValidator.greaterEqual0(), BuilderDescriptorState.Stable,
                "Ring rotation speed in blocks per second.", null);
        this.getDouble(data, "RelativeSpeed", this.relativeSpeed, 0.35, DoubleRangeValidator.fromExclToIncl(0.0, 2.0), BuilderDescriptorState.Stable,
                "Relative speed while on its spot (about OrbitSpeed / MaxSpeed).", null);
        this.getDouble(data, "CatchUpRelativeSpeed", this.catchUpRelativeSpeed, 0.7, DoubleRangeValidator.fromExclToIncl(0.0, 2.0), BuilderDescriptorState.Stable,
                "Relative speed when far from its spot.", null);
        this.getDouble(data, "LeadTime", this.leadTime, 0.6, DoubleSingleValidator.greaterEqual0(), BuilderDescriptorState.Stable,
                "Seconds ahead along the ring to aim at.", null);
        this.getDouble(data, "RadiusJitter", this.radiusJitter, 1.2, DoubleSingleValidator.greaterEqual0(), BuilderDescriptorState.Stable,
                "Max random drift closer to / further from the target, in blocks.", null);
        this.getDouble(data, "SpacingJitter", this.spacingJitter, 20.0, DoubleRangeValidator.between(0.0, 90.0), BuilderDescriptorState.Stable,
                "Max random drift from the even spacing, in degrees.", null);
        this.getDouble(data, "SpeedJitter", this.speedJitter, 0.15, DoubleRangeValidator.between(0.0, 0.9), BuilderDescriptorState.Stable,
                "Max random speed variation, as a fraction.", null);
        this.requireFeature(Feature.AnyPosition);
        return this;
    }

    @Override
    public boolean validate(String configName, NPCLoadTimeValidationHelper validationHelper, ExecutionContext context,
                            Scope globalScope, List<String> errors) {
        boolean result = super.validate(configName, validationHelper, context, globalScope, errors);
        validationHelper.requireMotionControllerType(MotionControllerWalk.class);
        return result;
    }

    public double getRadius(BuilderSupport support) {
        return this.radius.get(support.getExecutionContext());
    }

    public double getOrbitSpeed(BuilderSupport support) {
        return this.orbitSpeed.get(support.getExecutionContext());
    }

    public double getRelativeSpeed(BuilderSupport support) {
        return this.relativeSpeed.get(support.getExecutionContext());
    }

    public double getCatchUpRelativeSpeed(BuilderSupport support) {
        return this.catchUpRelativeSpeed.get(support.getExecutionContext());
    }

    public double getLeadTime(BuilderSupport support) {
        return this.leadTime.get(support.getExecutionContext());
    }

    public double getRadiusJitter(BuilderSupport support) {
        return this.radiusJitter.get(support.getExecutionContext());
    }

    public double getSpacingJitter(BuilderSupport support) { // degrees
        return this.spacingJitter.get(support.getExecutionContext());
    }

    public double getSpeedJitter(BuilderSupport support) {
        return this.speedJitter.get(support.getExecutionContext());
    }
}
