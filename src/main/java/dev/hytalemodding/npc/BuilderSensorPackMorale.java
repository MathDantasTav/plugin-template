package dev.hytalemodding.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.DoubleHolder;
import com.hypixel.hytale.server.npc.asset.builder.validators.DoubleRangeValidator;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;

/**
 * Builder for the "PackMorale" sensor.
 */
public class BuilderSensorPackMorale extends BuilderSensorBase {

    public static final String TYPE_NAME = "PackMorale";

    protected final DoubleHolder lossFraction = new DoubleHolder();

    @Override
    public Sensor build(BuilderSupport support) {
        return new SensorPackMorale(this, support);
    }

    @Override
    public String getShortDescription() {
        return "Test whether the NPC's pack has lost too many members";
    }

    @Override
    public String getLongDescription() {
        return "Matches when more than LossFraction of the pack has died. Never matches outside a pack.";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Override
    public Builder<Sensor> readConfig(JsonElement data) {
        this.getDouble(data, "LossFraction", this.lossFraction, 0.5, DoubleRangeValidator.between(0.0, 1.0), BuilderDescriptorState.Stable,
                "Fraction of the pack that has to die before this matches.", null);
        return this;
    }

    public double getLossFraction(BuilderSupport support) {
        return this.lossFraction.get(support.getExecutionContext());
    }
}
