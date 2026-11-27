package dev.mathantastic.betterai.npc;

import org.joml.Vector3d;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared flight state for the BirdFlight body motion, one flight per flock.
 * Each flight moves a virtual leader point: first once around a circle, then straight out along a heading.
 * Every bird holds its own spot near that point, so the flock moves as one.
 */
final class BirdFlightRegistry {

    static final int OUT_DIR_X = 0;
    static final int OUT_DIR_Z = 1;
    static final int OUT_SPEED = 2;
    static final int OUT_CRUISE_FRACTION = 3;
    static final int OUT_HEIGHT_OFFSET = 4;
    static final int OUT_SIZE = 5;

    private static final long MEMBER_TIMEOUT_NANOS = 2_000_000_000L;
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double MAX_FLIGHT_SECONDS = 90.0;
    private static final double OBSTRUCTED_SECONDS = 0.75;
    private static final int MAX_EXTENSIONS = 8; // over a big forest we eventually give up and land on the trees
    private static final double EXTENSION_SECONDS = 10.0;
    private static final double MIN_TURN = Math.toRadians(60);
    private static final double FLEE_SPREAD = Math.toRadians(25);
    private static final double FLEE_LAP = Math.PI; // scared flocks only swing half way round before leaving
    private static final double EXIT_ALIGNMENT = Math.cos(Math.toRadians(15));
    private static final double WOBBLE = Math.toRadians(15);
    private static final double WOBBLE_FOLLOW_RATE = 0.4;

    private static final double POINT_SPEED_FACTOR = 0.85; // leader point is a bit slower so birds can keep up
    private static final double STRAGGLER_DISTANCE = 10.0;
    private static final double STRAGGLER_RANGE = 20.0;
    private static final double SPOT_RADIUS = 3.5;
    private static final double SPOT_HEIGHT = 1.5;
    private static final double SEPARATION_WEIGHT = 1.5;

    private static final Map<Object, Flight> FLIGHTS = new HashMap<>();
    private static final Map<Object, Double> LAST_HEADING = new HashMap<>();

    private BirdFlightRegistry() {
    }

    record Settings(double relativeSpeed, double minDistance, double maxDistance, double circleRadius,
                    double separationDistance, boolean flee) {
    }

    // Fills out (see OUT_*) and returns true once the flock should land.
    static synchronized boolean update(Object flockKey, Object self, Vector3d pos, Vector3d threat, boolean obstructed,
                                       double maxSpeed, Settings settings, double[] out) {
        long now = System.nanoTime();
        if (FLIGHTS.size() > 64) {
            FLIGHTS.values().removeIf(f -> f.isEmpty(now));
        }

        Flight flight = FLIGHTS.get(flockKey);
        if (flight == null || flight.isEmpty(now) || (flight.landing && !flight.members.containsKey(self))) {
            flight = new Flight(pos, nextHeading(flockKey), settings, maxSpeed, now);
            FLIGHTS.put(flockKey, flight);
        }
        Member me = flight.members.get(self);
        if (me == null) {
            me = new Member(now);
            flight.members.put(self, me);
        }
        double dt = Math.max(1.0e-3, (now - me.lastSeen) / 1.0e9);
        me.x = pos.x;
        me.z = pos.z;
        me.lastSeen = now;

        if (settings.flee() && threat != null && !flight.flee) {
            flight.startFlee(pos, threat, settings, maxSpeed);
            LAST_HEADING.put(flockKey, flight.baseHeading);
        }

        me.obstructedTime = obstructed && flight.cruising ? me.obstructedTime + dt : 0.0;
        if (me.obstructedTime > OBSTRUCTED_SECONDS) {
            flight.landing = true;
        }
        flight.advance(now);

        // Head for our spot next to the leader point, faster the further behind we are.
        double toX = flight.pointX + me.spotX - pos.x;
        double toZ = flight.pointZ + me.spotZ - pos.z;
        double distance = Math.hypot(toX, toZ);
        double pull = Math.min(distance / 3.0, 2.5) / Math.max(distance, 1.0e-3);
        double dirX = flight.velX + toX * pull;
        double dirZ = flight.velZ + toZ * pull;

        for (Map.Entry<Object, Member> e : flight.members.entrySet()) {
            Member other = e.getValue();
            if (e.getKey() == self) {
                continue;
            }
            double dx = pos.x - other.x;
            double dz = pos.z - other.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < settings.separationDistance() * settings.separationDistance()) {
                double inv = 1.0 / Math.max(d2, 0.05);
                dirX += dx * inv * SEPARATION_WEIGHT;
                dirZ += dz * inv * SEPARATION_WEIGHT;
            }
        }

        double catchUp = Math.max(0.8, Math.min(1.8, 1.0 + (distance - 2.0) / 10.0));
        out[OUT_DIR_X] = dirX;
        out[OUT_DIR_Z] = dirZ;
        out[OUT_SPEED] = Math.min(Math.max(1.0, flight.relativeSpeed), flight.relativeSpeed * catchUp);
        out[OUT_CRUISE_FRACTION] = flight.cruiseFraction;
        out[OUT_HEIGHT_OFFSET] = me.spotY;
        return flight.landing;
    }

    // Calls off a landing and flies on a bit further.
    static synchronized void extend(Object flockKey, double extraDistance) {
        Flight flight = FLIGHTS.get(flockKey);
        if (flight == null || !flight.landing || !flight.cruising || flight.extensions >= MAX_EXTENSIONS) {
            return;
        }
        flight.extensions++;
        flight.landing = false;
        flight.targetDistance = flight.traveled + extraDistance;
    }

    static synchronized void leave(Object flockKey, Object self) {
        Flight flight = FLIGHTS.get(flockKey);
        if (flight == null) {
            return;
        }
        flight.members.remove(self);
        if (flight.members.isEmpty()) {
            FLIGHTS.remove(flockKey);
        }
    }

    // A random heading, at least MIN_TURN away from the flock's last one.
    private static double nextHeading(Object flockKey) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Double last = LAST_HEADING.get(flockKey);
        double heading = last == null
                ? random.nextDouble(0, TWO_PI)
                : last + random.nextDouble(MIN_TURN, TWO_PI - MIN_TURN);
        if (LAST_HEADING.size() > 256) {
            LAST_HEADING.clear();
        }
        LAST_HEADING.put(flockKey, heading);
        return heading;
    }

    private static final class Member {
        final double spotX, spotY, spotZ;
        double x, z;
        double obstructedTime;
        long lastSeen;

        Member(long now) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double angle = random.nextDouble(0, TWO_PI);
            double radius = SPOT_RADIUS * Math.sqrt(random.nextDouble());
            this.spotX = Math.cos(angle) * radius;
            this.spotZ = Math.sin(angle) * radius;
            this.spotY = random.nextDouble(-SPOT_HEIGHT, SPOT_HEIGHT);
            this.lastSeen = now;
        }
    }

    private static final class Flight {
        final Map<Object, Member> members = new IdentityHashMap<>();
        final double cruiseFraction;
        final int spin;
        long created, lastAdvance, nextWobbleChange;
        double relativeSpeed, pointSpeed, targetDistance, traveled;
        double pointX, pointZ, velX, velZ;
        double centerX, centerZ, circleRadius, theta, turned, lap = TWO_PI;
        double baseHeading, wobble, wobbleTarget;
        int extensions;
        boolean cruising, landing, flee;

        Flight(Vector3d start, double heading, Settings settings, double maxSpeed, long now) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            this.created = now;
            this.lastAdvance = now;
            this.nextWobbleChange = now;
            this.baseHeading = heading;
            this.cruiseFraction = random.nextDouble(0.25, 0.75);
            this.spin = random.nextBoolean() ? 1 : -1;
            this.setSpeed(settings, maxSpeed);
            this.targetDistance = random.nextDouble(settings.minDistance(), Math.max(settings.minDistance() + 1.0e-3, settings.maxDistance()));

            this.circleRadius = settings.circleRadius();
            this.startLap(start, TWO_PI);
        }

        // The circle passes through the start, so the point begins right where the flock is.
        void startLap(Vector3d start, double lap) {
            this.theta = ThreadLocalRandom.current().nextDouble(0, TWO_PI);
            this.centerX = start.x - Math.cos(this.theta) * this.circleRadius;
            this.centerZ = start.z - Math.sin(this.theta) * this.circleRadius;
            this.pointX = start.x;
            this.pointZ = start.z;
            this.turned = 0.0;
            this.lap = lap;
            this.cruising = false;
        }

        void setSpeed(Settings settings, double maxSpeed) {
            this.relativeSpeed = settings.relativeSpeed();
            this.pointSpeed = Math.max(1.0, settings.relativeSpeed() * maxSpeed * POINT_SPEED_FACTOR);
        }

        boolean isEmpty(long now) {
            this.members.values().removeIf(m -> now - m.lastSeen > MEMBER_TIMEOUT_NANOS);
            return this.members.isEmpty();
        }

        void startFlee(Vector3d pos, Vector3d threat, Settings settings, double maxSpeed) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            this.baseHeading = Math.atan2(pos.z - threat.z, pos.x - threat.x) + random.nextDouble(-FLEE_SPREAD, FLEE_SPREAD);
            this.wobble = 0.0;
            this.flee = true;
            this.landing = false;
            this.traveled = 0.0;
            this.targetDistance = random.nextDouble(settings.minDistance(), Math.max(settings.minDistance() + 1.0e-3, settings.maxDistance()));
            this.setSpeed(settings, maxSpeed);
            this.circleRadius = settings.circleRadius();
            this.startLap(pos, FLEE_LAP);
        }

        // Moves the leader point; called by every member, but time based so it only moves once per tick.
        void advance(long now) {
            double elapsed = (now - this.lastAdvance) / 1.0e9;
            this.lastAdvance = now;
            if (elapsed <= 0.0) {
                return;
            }
            double step = this.pointSpeed * this.waitForStragglers() * elapsed;

            if (!this.cruising) {
                double angularStep = step / this.circleRadius;
                this.theta += this.spin * angularStep;
                this.turned += angularStep;
                this.pointX = this.centerX + Math.cos(this.theta) * this.circleRadius;
                this.pointZ = this.centerZ + Math.sin(this.theta) * this.circleRadius;
                this.velX = -Math.sin(this.theta) * this.spin;
                this.velZ = Math.cos(this.theta) * this.spin;
                // Leave once we've gone around and are facing the way out.
                if (this.turned >= this.lap
                        && this.velX * Math.cos(this.baseHeading) + this.velZ * Math.sin(this.baseHeading) > EXIT_ALIGNMENT) {
                    this.cruising = true;
                }
            } else {
                this.tickWobble(now, elapsed);
                double heading = this.baseHeading + this.wobble;
                this.velX = Math.cos(heading);
                this.velZ = Math.sin(heading);
                this.pointX += this.velX * step;
                this.pointZ += this.velZ * step;
                this.traveled += step;
                if (this.traveled >= this.targetDistance) {
                    this.landing = true;
                }
            }
            if ((now - this.created) / 1.0e9 > MAX_FLIGHT_SECONDS + this.extensions * EXTENSION_SECONDS) {
                this.landing = true;
            }
        }

        // Slows the point down when the flock is falling behind it.
        private double waitForStragglers() {
            if (this.members.isEmpty()) {
                return 1.0;
            }
            double sumX = 0, sumZ = 0;
            for (Member m : this.members.values()) {
                sumX += m.x;
                sumZ += m.z;
            }
            int n = this.members.size();
            double behind = Math.hypot(sumX / n - this.pointX, sumZ / n - this.pointZ);
            return Math.max(0.3, Math.min(1.0, 1.0 - (behind - STRAGGLER_DISTANCE) / STRAGGLER_RANGE));
        }

        // Slow random sway around the base heading so the path isn't ruler straight.
        private void tickWobble(long now, double elapsed) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (now >= this.nextWobbleChange) {
                this.wobbleTarget = random.nextDouble(-WOBBLE, WOBBLE);
                this.nextWobbleChange = now + (long) (random.nextDouble(3.0, 6.0) * 1.0e9);
            }
            this.wobble += (this.wobbleTarget - this.wobble) * Math.min(1.0, WOBBLE_FOLLOW_RATE * elapsed);
        }
    }
}
