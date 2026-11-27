package dev.hytalemodding;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.hytalemodding.commands.ExampleCommand;
import dev.hytalemodding.config.ExampleConfig;
import dev.hytalemodding.events.ExampleEvent;
import dev.hytalemodding.npc.BuilderBodyMotionBirdFlight;
import dev.hytalemodding.npc.BuilderBodyMotionPackCircle;
import dev.hytalemodding.npc.BuilderSensorPackMorale;

import javax.annotation.Nonnull;

public class ExamplePlugin extends JavaPlugin {

    private static Config<ExampleConfig> config = null;

    public ExamplePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        config = this.withConfig("example_config", ExampleConfig.CODEC);
    }

    @Override
    protected void setup() {
        // Custom NPC components used by Template_PackPredator and Template_Birds_Passive
        NPCPlugin.get().registerCoreComponentType(BuilderBodyMotionPackCircle.TYPE_NAME, BuilderBodyMotionPackCircle::new);
        NPCPlugin.get().registerCoreComponentType(BuilderSensorPackMorale.TYPE_NAME, BuilderSensorPackMorale::new);
        NPCPlugin.get().registerCoreComponentType(BuilderBodyMotionBirdFlight.TYPE_NAME, BuilderBodyMotionBirdFlight::new);

        config.save();
        this.getCommandRegistry().registerCommand(new ExampleCommand("example", "An example command"));
        if (getConfig().get().isEnabledWelcomeMessage()) {
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, ExampleEvent::onPlayerReady);
        }
    }

    public static Config<ExampleConfig> getConfig() {
        return config;
    }
}