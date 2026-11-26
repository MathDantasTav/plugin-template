package dev.hytalemodding.npc;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared ring state for NPCs using the PackCircle body motion.
 * Each target gets one orbit with a rotating phase; members keep an offset from it that is
 * slowly evened out (keeping their order), plus a bit of random drift so it doesn't look perfect.
 */
final class PackCircleRegistry {

    static final int OUT_ANGLE = 0;
    static final int OUT_RADIUS_BIAS = 1;
    static final int OUT_SPEED_BIAS = 2;

    private static final double TWO_PI = Math.PI * 2.0;
    private static final long MEMBER_TIMEOUT_NANOS = 500_000_000L;
    private static final long ORBIT_TIMEOUT_NANOS = 10_000_000_000L;
    private static final double REBALANCE_SPEED = 1.2; // rad/s
    private static final double JITTER_CHANGE_MIN_SECONDS = 2.0;
    private static final double JITTER_CHANGE_MAX_SECONDS = 5.0;
    private static final double JITTER_FOLLOW_RATE = 0.6;

    private static final Map<Object, Orbit> ORBITS = new ConcurrentHashMap<>();

    private PackCircleRegistry() {
    }

    // Writes the angle to head for and the member's radius/speed bias (-1..1) into out.
    static void update(Object targetKey, Object member, double currentAngle, double angularSpeed, double leadAngle,
                       double spacingJitter, double[] out) {
        long now = System.nanoTime();
        if (ORBITS.size() > 32) {
            ORBITS.entrySet().removeIf(e -> e.getValue().isStale(now));
        }
        Orbit orbit = ORBITS.computeIfAbsent(targetKey, k -> new Orbit(now, currentAngle));
        synchronized (orbit) {
            orbit.update(member, currentAngle, angularSpeed, leadAngle, spacingJitter, now, out);
        }
    }

    static void leave(Object targetKey, Object member) {
        if (targetKey == null) {
            return;
        }
        Orbit orbit = ORBITS.get(targetKey);
        if (orbit == null) {
            return;
        }
        synchronized (orbit) {
            orbit.members.remove(member);
            if (orbit.members.isEmpty()) {
                ORBITS.remove(targetKey, orbit);
            }
        }
    }

    private static double normalize(double angle) {
        double a = angle % TWO_PI;
        return a < 0 ? a + TWO_PI : a;
    }

    private static double wrapToPi(double angle) {
        double a = normalize(angle + Math.PI);
        return a - Math.PI;
    }

    private static final class Member {
        double offset;
        long lastSeen;

        final double[] jitter = new double[3];
        final double[] jitterTarget = new double[3];
        long nextJitterChange;

        Member(double offset, long now) {
            this.offset = offset;
            this.lastSeen = now;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                this.jitter[i] = random.nextDouble(-1.0, 1.0);
            }
            this.pickJitterTargets(now);
        }

        void pickJitterTargets(long now) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                this.jitterTarget[i] = random.nextDouble(-1.0, 1.0);
            }
            double seconds = random.nextDouble(JITTER_CHANGE_MIN_SECONDS, JITTER_CHANGE_MAX_SECONDS);
            this.nextJitterChange = now + (long) (seconds * 1.0e9);
        }

        void tickJitter(double dt, long now) {
            if (now >= this.nextJitterChange) {
                this.pickJitterTargets(now);
            }
            double t = Math.min(1.0, JITTER_FOLLOW_RATE * dt);
            for (int i = 0; i < 3; i++) {
                this.jitter[i] += (this.jitterTarget[i] - this.jitter[i]) * t;
            }
        }
    }

    private static final class Orbit {
        final int direction;
        final Map<Object, Member> members = new IdentityHashMap<>();
        double phase;
        long lastAdvance;
        long lastRebalance;
        long lastUsed;

        Orbit(long now, double startAngle) {
            this.direction = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            this.phase = startAngle;
            this.lastAdvance = now;
            this.lastRebalance = now;
            this.lastUsed = now;
        }

        boolean isStale(long now) {
            return now - this.lastUsed > ORBIT_TIMEOUT_NANOS;
        }

        void update(Object self, double currentAngle, double angularSpeed, double leadAngle, double spacingJitter,
                    long now, double[] out) {
            double elapsed = (now - this.lastAdvance) / 1.0e9;
            this.lastAdvance = now;
            this.lastUsed = now;
            this.phase = normalize(this.phase + this.direction * angularSpeed * elapsed);

            this.members.entrySet().removeIf(e -> e.getKey() != self && now - e.getValue().lastSeen > MEMBER_TIMEOUT_NANOS);
            Member me = this.members.get(self);
            if (me == null) {
                me = new Member(normalize(currentAngle - this.phase), now);
                this.members.put(self, me);
            }
            double memberDt = (now - me.lastSeen) / 1.0e9;
            me.lastSeen = now;
            me.tickJitter(memberDt, now);

            rebalance(now);

            out[OUT_ANGLE] = this.phase + me.offset + me.jitter[0] * spacingJitter + this.direction * leadAngle;
            out[OUT_RADIUS_BIAS] = me.jitter[1];
            out[OUT_SPEED_BIAS] = me.jitter[2];
        }

        private void rebalance(long now) {
            double maxStep = REBALANCE_SPEED * (now - this.lastRebalance) / 1.0e9;
            this.lastRebalance = now;
            int n = this.members.size();
            if (n < 2 || maxStep <= 0) {
                return;
            }
            List<Member> sorted = new ArrayList<>(this.members.values());
            sorted.sort((a, b) -> Double.compare(normalize(a.offset), normalize(b.offset)));
            double spacing = TWO_PI / n;

            // Anchor at the average so the formation stays put instead of sliding towards one member.
            double sumX = 0;
            double sumZ = 0;
            for (int k = 0; k < n; k++) {
                double base = normalize(sorted.get(k).offset) - k * spacing;
                sumX += Math.cos(base);
                sumZ += Math.sin(base);
            }
            double anchor = Math.atan2(sumZ, sumX);

            for (int k = 0; k < n; k++) {
                Member m = sorted.get(k);
                double target = anchor + k * spacing;
                double diff = wrapToPi(target - m.offset);
                m.offset += Math.max(-maxStep, Math.min(maxStep, diff));
            }
        }
    }
}
