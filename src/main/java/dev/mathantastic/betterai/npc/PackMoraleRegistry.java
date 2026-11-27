package dev.mathantastic.betterai.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks how much of each pack (by flock id) has died, for the PackMorale sensor.
 * A member whose ref is no longer valid counts as dead; the pack remembers its peak size.
 */
final class PackMoraleRegistry {

    private static final Map<UUID, Pack> PACKS = new ConcurrentHashMap<>();

    private PackMoraleRegistry() {
    }

    // Returns the fraction of the pack's peak size that has died.
    static double checkIn(UUID flockId, Ref<EntityStore> member) {
        if (PACKS.size() > 64) {
            PACKS.entrySet().removeIf(e -> e.getValue().isGone());
        }
        Pack pack = PACKS.computeIfAbsent(flockId, id -> new Pack());
        synchronized (pack) {
            return pack.checkIn(member);
        }
    }

    private static final class Pack {
        final Map<Ref<EntityStore>, Boolean> members = new IdentityHashMap<>();
        int peak;

        double checkIn(Ref<EntityStore> member) {
            this.members.put(member, Boolean.TRUE);
            int alive = 0;
            for (Ref<EntityStore> ref : this.members.keySet()) {
                if (ref.isValid()) {
                    alive++;
                }
            }
            this.peak = Math.max(this.peak, this.members.size());
            int lost = this.members.size() - alive;
            return this.peak == 0 ? 0.0 : (double) lost / this.peak;
        }

        synchronized boolean isGone() {
            for (Ref<EntityStore> ref : this.members.keySet()) {
                if (ref.isValid()) {
                    return false;
                }
            }
            return true;
        }
    }
}
