package dev.mathantastic.betterai;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.mathantastic.betterai.npc.BuilderBodyMotionBirdFlight;
import dev.mathantastic.betterai.npc.BuilderBodyMotionPackCircle;
import dev.mathantastic.betterai.npc.BuilderSensorPackMorale;

import javax.annotation.Nonnull;

/**
 * Better AI by Mathantastic Productions: registers the custom NPC components used by the mod's role templates.
 */
public class BetterAIPlugin extends JavaPlugin {

    public BetterAIPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Custom NPC components used by Template_PackPredator and Template_Birds_Passive
        NPCPlugin.get().registerCoreComponentType(BuilderBodyMotionPackCircle.TYPE_NAME, BuilderBodyMotionPackCircle::new);
        NPCPlugin.get().registerCoreComponentType(BuilderSensorPackMorale.TYPE_NAME, BuilderSensorPackMorale::new);
        NPCPlugin.get().registerCoreComponentType(BuilderBodyMotionBirdFlight.TYPE_NAME, BuilderBodyMotionBirdFlight::new);
    }
}
