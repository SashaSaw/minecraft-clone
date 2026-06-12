# Craft — a Minecraft-style voxel game in Java

A from-scratch Minecraft clone: infinite procedurally generated plains-and-ocean world,
vanilla-faithful physics and mechanics, day/night cycle, survival systems, crafting,
smelting, and mobs. Java 21 + LWJGL 3 + OpenGL 3.3 core. **All textures are generated
procedurally in code at startup — there are no image assets.**

## Run

```bash
gradle run                 # opens the title screen
gradle run --args="12345"  # jump straight into a world with this seed
```

From the title screen: **Singleplayer** lists your saved worlds (newest first) and
lets you create a new one; in-game, **Esc** opens the pause menu with Resume and
Save & Quit to Title.

Requires Java 21+. Worlds save to `saves/world-<seed>/` automatically (autosave every
45 s and on quit); relaunching with the same seed resumes where you left off.

## Controls

| Input | Action |
|---|---|
| Mouse | Look |
| W A S D | Move |
| Space | Jump / swim up |
| Double-tap W or Ctrl | Sprint (needs hunger > 6) |
| Shift | Sneak |
| Left click | Break block (hold) / attack mob |
| Right click | Place block / use / eat / open crafting table & furnace |
| Scroll or 1-9 | Select hotbar slot |
| E | Inventory (with 2x2 crafting) |
| Q | Drop one item |
| F2 | Screenshot |
| F3 | Debug overlay |
| Esc | Pause menu (Resume / Save & Quit to Title) |

## What's implemented

- **World**: infinite chunk-streamed terrain from continentalness-spline noise
  (plains, beaches, oceans with gravel patches), oak trees with vanilla canopy shapes,
  tall grass, flowers, sugar cane, coal/iron ore veins, bedrock floor. Deterministic
  per-chunk seeding; background generation threads.
- **Rendering**: per-section culled meshes with the 0fps per-vertex ambient-occlusion
  + smooth-lighting algorithm and anisotropy quad flip, vanilla face shading,
  cutout leaves/plants, blended sorted water, distance fog matched to the sky.
- **Lighting**: 0–15 sky + block light with BFS propagation (sunlight-column rule),
  two-queue removal, torches and lit furnaces as light sources, day/night sky darken.
- **Sky**: 20-minute day cycle, gradient dome, sun/moon billboards, sunset/sunrise
  horizon glow, stars at night.
- **Player**: vanilla per-tick physics (walk 4.317 m/s, sprint 5.612 + FOV boost,
  jump 0.42 → 1.25 blocks, swimming), AABB collision, fall damage, drowning with
  air bubbles, 20 HP + hunger/saturation/exhaustion (sprint gate, regen, starvation),
  death screen with inventory drop + respawn.
- **Blocks**: break with vanilla hardness/tool-speed formula and 10-stage crack
  overlay, correct drop tables (stone→cobble needs a pickaxe, etc.), item entities
  with magnet pickup, block placement with player-collision check.
- **Items & crafting**: 36-slot inventory, hotbar, shift/half-stack click rules,
  2x2 + 3x3 shaped recipes (planks, sticks, table, furnace, torches, wood/stone/iron
  tools), furnace smelting with fuel burn times (sand→glass, raw iron→ingot,
  log→charcoal, meat cooking).
- **Mobs**: chickens (slow-fall), cows, pigs, sheep (graze grass to dirt) with
  wander/panic/look-at-player AI spawning in herds on grass; zombies spawn in packs
  at night in low light, chase within 16 blocks, melee with knockback, burn in
  daylight, despawn far away. Animated box models with head tracking, walk cycles,
  hurt flash, drops (porkchops, beef, leather, wool, mutton, feathers, rotten flesh).

## Notable simplifications vs. vanilla

No water flow physics, no half blocks/stairs, single biome (plains + ocean), no caves,
tools have no durability, no hostile mobs other than zombies, entities don't persist
in saves, floor torches only.
