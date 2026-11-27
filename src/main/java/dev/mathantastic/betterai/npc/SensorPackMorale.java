package dev.mathantastic.betterai.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.flock.FlockMembership;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import java.util.UUID;

/**
 * Matches when more than LossFraction of the NPC's pack has died.
 */
public class SensorPackMorale extends SensorBase {

    protected final double lossFraction;

    public SensorPackMorale(BuilderSensorPackMorale builder, BuilderSupport support) {
        super(builder);
        this.lossFraction = builder.getLossFraction(support);
    }

    @Override
    public boolean matches(Ref<EntityStore> ref, ExecutionSupport support, double dt, Store<EntityStore> store) {
        if (!super.matches(ref, support, dt, store)) {
            return false;
        }
        FlockMembership membership = store.getComponent(ref, FlockMembership.getComponentType());
        if (membership == null) {
            return false;
        }
        UUID flockId = membership.getFlockId();
        if (flockId == null) {
            return false;
        }
        return PackMoraleRegistry.checkIn(flockId, ref) > this.lossFraction;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
