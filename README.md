![Hytale Modding](docs/banner.jpeg)

# Hytale Modding

## Better AI: Smarter Wolves and Birds for Hytale

### A server mod that gives Hytale's creatures group behavior: wolves that hunt as a pack and birds that fly as a real flock.

[![Better AI overview video](https://img.youtube.com/vi/yit8Vit4mYI/hqdefault.jpg)](https://www.youtube.com/watch?v=yit8Vit4mYI)

## The Problem

In vanilla Hytale, animals that come in groups don't act like groups. Each wolf picks its own target and runs at it alone. Pigeons spawn together but move on their own, flying in tight loops around their home spot and never really going anywhere.

I wanted to make wild creatures feel alive: wolves that hunt as a pack and birds that move as one flock across the landscape.

## Approach: JSON for decisions, Java for movement

Hytale NPCs use JSON role files as state machines, sensors check conditions and trigger actions or movement. I kept everything possible in JSON, following vanilla conventions and making states, timers, alerts, animations, flock joining and spawns easy to tune.

I only used Java where JSON fell short:

- **Shared state:** Each NPC has its own JSON instance, so coordinated flocks need a shared registry keyed by flock ID.
- **Custom movement math:** Orbits, flocking forces, easing curves and terrain look-ahead require per-tick calculations beyond vanilla movement.
- **World data:** Accessing block types, distant ground height and dead flock members requires Java.

Each Java feature is registered as a component type, usable from JSON like any vanilla component (e.g. `"Type": "BirdFlight"`), with configurable parameters.

## Wolves: hunting as a pack

- **Packs of 4–6:** I added a custom flock definition and spawn override (JSON).
- **Surrounding the prey (Java, `PackCircle`):** Before attacking, the pack spreads into a ring around its target. A shared orbit keeps them evenly spaced and in order, with slight drift so it looks natural and not robotic. *Why Java:* each wolf has to know where the others are on the ring.
- **Breaking morale (Java, `PackMorale`):** Once enough of the pack has died, the survivors flee. *Why Java:* no vanilla sensor counts dead packmates, and dead NPCs can't send alerts.
- **Black wolf variant:** `Wolf_Black` is a short JSON variant of the new template.

### Vanilla

[![Vanilla wolves video](https://img.youtube.com/vi/cDeS2clS_aQ/hqdefault.jpg)](https://www.youtube.com/watch?v=cDeS2clS_aQ)

### Modded

[![Modded wolves video](https://img.youtube.com/vi/uvZ--d2dnKk/hqdefault.jpg)](https://www.youtube.com/watch?v=uvZ--d2dnKk)

## Birds: flying as a real flock

- **Flock behavior (JSON + Java):** Flocks take off together, circle, fly 90–180 blocks, land, wander, and repeat. Each flock follows one shared flight plan, with birds maintaining loose positions, catching up when behind, and avoiding neighbors. This prevents the splitting I encountered when birds relied only on neighbor-based steering. Small flocks also merge using vanilla's existing wolf merge rule.
- **Terrain-aware flight and landings (Java):** Each bird scans the ground below and up to 35 blocks ahead, including treetops, to anticipate obstacles and smoothly adjust altitude. Vanilla's controller only checks 6 blocks down, causing birds to climb indefinitely over higher terrain. Landings use gradual gliding and deceleration, avoid treetops, and switch to walking on touchdown.
- **Group reactions (JSON):** When one bird is hurt or spots a nearby player, it alerts the entire flock. They scatter together, circle briefly with wings flapping, then fly away.

### Vanilla

[![Vanilla birds video](https://img.youtube.com/vi/A-uMP6OJjjQ/hqdefault.jpg)](https://www.youtube.com/watch?v=A-uMP6OJjjQ)

### Modded

[![Modded birds video](https://img.youtube.com/vi/iaodaVNGKME/hqdefault.jpg)](https://www.youtube.com/watch?v=iaodaVNGKME)

## Design decision

- **Override instead of add:** I replaced the vanilla bird template and spawn files, so every existing bird and wolf gets the new behavior without editing each creature.
- **Lightweight shared state:** The registries are small shared tables that clean up after themselves. They need no saving and work across any number of flocks.
- **Tunable from JSON:** Heights, distances, speeds and ground time are all parameters, so each species can be tuned without recompiling.
- **Performance:** Terrain checks run four times a second per bird, not every tick.

Matheus Tavares
