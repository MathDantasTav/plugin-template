package dev.hytalemodding.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.BooleanHolder;
import com.hypixel.hytale.server.npc.asset.builder.holder.DoubleHolder;
import com.hypixel.hytale.server.npc.asset.builder.validators.DoubleRangeValidator;
import com.hypixel.hytale.server.npc.asset.builder.validators.DoubleSingleValidator;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderBodyMotionBase;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerFly;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import com.hypixel.hytale.server.npc.util.expression.Scope;
import com.hypixel.hytale.server.npc.validators.NPCLoadTimeValidationHelper;

import java.util.List;

/**
 * Builder for the "BirdFlight" body motion. Only does anything while the Fly controller is active.
 */
public class BuilderBodyMotionBirdFlight extends BuilderBodyMotionBase {

    public static final String TYPE_NAME = "BirdFlight";

    protected final DoubleHolder relativeSpeed = new DoubleHolder();
    protected final DoubleHolder flightDistanceMin = new DoubleHolder();
    protected final DoubleHolder flightDistanceMax = new DoubleHolder();
    protected final DoubleHolder circleRadius = new DoubleHolder();
    protected final DoubleHolder cruiseHeightMin = new DoubleHolder();
    protected final DoubleHolder cruiseHeightMax = new DoubleHolder();
    protected final DoubleHolder separationDistance = new DoubleHolder();
    protected final BooleanHolder fleeFromTarget = new BooleanHolder();

    @Override
    public BodyMotionBirdFlight build(BuilderSupport support) {
        return new BodyMotionBirdFlight(this, support);
    }

    @Override
    public String getShortDescription() {
        return "Fly with the flock in one direction, then land";
    }

    @Override
    public String getLongDescription() {
        return "Flies a lap with the flock, then along a shared heading for a random distance, then lands and switches to Walk.";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Override
    public BuilderBodyMotionBirdFlight readConfig(JsonElement data) {
        this.getDouble(data, "RelativeSpeed", this.relativeSpeed, 0.5, DoubleRangeValidator.fromExclToIncl(0.0, 2.0), BuilderDescriptorState.Stable,
                "Relative flight speed.", null);
        this.getDouble(data, "FlightDistanceMin", this.flightDistanceMin, 90.0, DoubleSingleValidator.greater0(), BuilderDescriptorState.Stable,
                "Shortest distance to fly before landing.", null);
        this.getDouble(data, "FlightDistanceMax", this.flightDistanceMax, 180.0, DoubleSingleValidator.greater0(), BuilderDescriptorState.Stable,
                "Longest distance to fly before landing.", null);
        this.getDouble(data, "CircleRadius", this.circleRadius, 12.0, DoubleSingleValidator.greater0(), BuilderDescriptorState.Stable,
                "Radius of the lap the flock flies before heading off.", null);
        this.getDouble(data, "CruiseHeightMin", this.cruiseHeightMin, 16.0, DoubleSingleValidator.greaterEqual0(), BuilderDescriptorState.Stable,
                "Lowest height over ground to cruise at.", null);
        this.getDouble(data, "CruiseHeightMax", this.cruiseHeightMax, 26.0, DoubleSingleValidator.greaterEqual0(), BuilderDescriptorState.Stable,
                "Highest height over ground to cruise at.", null);
        this.getDouble(data, "SeparationDistance", this.separationDistance, 2.0, DoubleSingleValidator.greater0(), BuilderDescriptorState.Stable,
                "Flockmates closer than this push apart.", null);
        this.getBoolean(data, "FleeFromTarget", this.fleeFromTarget, false, BuilderDescriptorState.Stable,
                "Turn the flock away from the sensed target.", null);
        return this;
    }

    @Override
    public boolean validate(String configName, NPCLoadTimeValidationHelper validationHelper, ExecutionContext context,
                            Scope globalScope, List<String> errors) {
        boolean result = super.validate(configName, validationHelper, context, globalScope, errors);
        validationHelper.requireMotionControllerType(MotionControllerFly.class);
        return result;
    }

    public double getRelativeSpeed(BuilderSupport support) {
        return this.relativeSpeed.get(support.getExecutionContext());
    }

    public double getFlightDistanceMin(BuilderSupport support) {
        return this.flightDistanceMin.get(support.getExecutionContext());
    }

    public double getFlightDistanceMax(BuilderSupport support) {
        return this.flightDistanceMax.get(support.getExecutionContext());
    }

    public double getCircleRadius(BuilderSupport support) {
        return this.circleRadius.get(support.getExecutionContext());
    }

    public double getCruiseHeightMin(BuilderSupport support) {
        return this.cruiseHeightMin.get(support.getExecutionContext());
    }

    public double getCruiseHeightMax(BuilderSupport support) {
        return this.cruiseHeightMax.get(support.getExecutionContext());
    }

    public double getSeparationDistance(BuilderSupport support) {
        return this.separationDistance.get(support.getExecutionContext());
    }

    public boolean isFleeFromTarget(BuilderSupport support) {
        return this.fleeFromTarget.get(support.getExecutionContext());
    }
}
