# Minecraft Clone — Consolidated Implementation Reference

Distilled from research into Minecraft wiki, decompiled-source writeups, 0fps.net,
Seed of Andromeda lighting posts, jdah/minecraft-weekend, fogleman/Craft, Hopson97.
All units: 1 block = 1 m, 20 ticks/sec (1 tick = 50 ms).

## 1. Engine / rendering

- LWJGL 3.3.6 BOM, natives-macos-arm64, JOML 1.10.8. macOS needs `-XstartOnFirstThread`
  and `GLFW_OPENGL_FORWARD_COMPAT`. GL 3.3 core.
- Fixed timestep loop: tick() at 20 TPS, render every frame with `partialTick = acc/TICK`
  interpolation. Clamp frame delta to 0.25 s.
- Chunks: 16×16 column × 128 high, flat `byte[]` indexed `(y*16+z)*16+x`; mesh per 16³
  section (8 sections/column). Long-keyed chunk map: `(x & 0xFFFFFFFFL) | (z << 32)`.
- Culled mesher (not greedy): emit face only if neighbor is air/transparent; needs
  neighbor chunks loaded. Dirty section on block change; coord 0/15 → dirty neighbor too.
- Vertex: pos(3f, chunk-local) + uv(2f) + a packed light/AO/face term. One shared global
  index buffer (0,1,2,2,3,0 pattern). One VAO+VBO per section per layer.
- Frustum culling: JOML `FrustumIntersection.testAab` on section AABBs.
- Threading: worldgen+meshing on ExecutorService(cores-1), ALL GL on main thread.
  Workers produce ByteBuffers (MemoryUtil.memAlloc, pooled) → ConcurrentLinkedQueue →
  main thread uploads ≤ 4-8/frame. Mesh only when 4 neighbors loaded.
- Pitfalls: free GL buffers on unload; pool direct buffers; never GL off-thread;
  queue remeshes (never synchronous on edit); water top face at y+0.875 to avoid
  z-fighting; dirty neighbors on border edits.

### Render passes per frame
1. clear(fogColor); draw sky (depth write off): sky gradient, sunset glow band, sun/moon
   quads (additive blend), stars at night, optional clouds y=192 drifting -X.
2. Opaque + cutout terrain (front-to-back; cutout shader: `if (a < 0.5) discard`;
   leaves render both/inner faces — don't cull leaf↔leaf; cross plants = 2 quads, no cull).
3. Translucent (water): blend SRC_ALPHA/ONE_MINUS, depth test ON, chunks sorted
   back-to-front (or depth write off as v1). Water alpha ~0.75. Top face double-sided.

### Texture atlas
16×16-px tiles in a 256×256 atlas (tile = 1/16 of UV). GL_NEAREST min+mag. No mipmaps v1.
Inset UVs by half-texel (±0.5/256) at tile edges. (Alternative: GL_TEXTURE_2D_ARRAY.)

### Lighting (the Minecraft model)
- Two 4-bit channels/block: skyLight, blockLight. Final = `max(block, sky - skyDarken)`.
- Block light BFS: torch=14, step `level - 1` through 6 face neighbors into transparent.
- Sky light: top-exposed cells = 15; downward propagation of level-15 does NOT decrement
  (sunlight shafts); horizontal/lower always -1.
- Removal: two-queue (carry old level; neighbor < carried → zero+removal queue; else
  re-propagation queue; then normal BFS from re-prop queue).
- Brightness curve: `a=level/15; b = a/(4-3a)`, lerp small ambient ~0.04. (Or 0.8^(15-l).)
- Per-face multipliers: top 1.0, bottom 0.5, ±Z 0.8, ±X 0.6.
- AO (0fps): per vertex, 3 outside-plane neighbors (side1, side2, corner):
  `ao = (s1&&s2) ? 0 : 3-(s1+s2+corner)` → multiplier {0.4,0.6,0.8,1.0}.
  Smooth light = average light of face-neighbor + side1 + side2 + corner (skip opaque).
  Quad flip: if `a00+a11 > a01+a10` triangulate 0-1-2/2-3-0 else 1-2-3/3-0-1.

### Sky / day-night
- 24000 ticks = 20 min. 0=sunrise, 6000=noon, 12000=sunset start, 13800-22200 night,
  18000 midnight, 22200-24000 dawn. Monsters surface-spawn from 13188 to 22812.
- `frac = timeOfDay/24000 - 0.25` (wrap); celestialAngle ≈ frac (vanilla smooths slightly);
  sun rotates around E-W axis by angle·2π; rises east (+X); moon opposite.
- `d = clamp(0.5 + 2cos(angle·2π), 0, 1)`; `skyDarken = round((1-d)*11)` (0 day, 11 night);
  effective sky light at night = 4 (moonlight).
- Day sky #78A7FF (0.47,0.65,1.0), day fog/horizon #C0D8FF; night ≈ #02040C. Multiply day
  color by d (with floor ~0.06).
- Sunset glow when |cos(angle·2π)| < 0.4: f = cos/0.4*0.5+0.5;
  r=0.7+f*0.3, g=0.2+f²*0.7, b=0.2, a=1-(1-sin(fπ))*0.99 — orange horizon band toward sun.
- Sun quad half-size 30 @ dist 100; moon 20 @ 100; additive blend. Stars ~1000-1500 quads
  size 0.15-0.25 on r=100 sphere, alpha = night factor.
- Fog: linear, `fogEnd = renderDist*16`, `fogStart = 0.75*fogEnd`, fog color == clear color
  == horizon color (hides chunk pop-in). Underwater: dense, color ~(0.1,0.15,0.45), end ~24.

## 2. Terrain generation

- Noise: improved Perlin w/ seeded perm table (or OpenSimplex2S), fBm:
  lacunarity 2.0, persistence 0.5.
- Layers: continentalness c = fbm(3 oct, wavelength ~800); detail d = fbm(4 oct, ~128).
- Spline (c → base height), piecewise linear through:
  (-1.0,46) (-0.5,48) (-0.2,56) (-0.05,62) (0.10,66) (1.0,70)
- amp = lerpClamped(c, -0.2→2.0, 0.1→5.0); `h = clamp(round(base + d*amp), 40, 90)`.
- SEA_LEVEL = 62 (topmost water block y=62). Plains 62-75, ocean floor 45-58.
- Column: y0 bedrock; stone up to h-dirtDepth-1 (dirtDepth 3-4); dirt; surface:
  h≥64 grass; 59≤h≤63 sand (sand replaces dirt too); h<59 sand with gravel patches where
  1-oct noise(wavelength 24) > 0.4. Water fills h+1..62.
- Two-phase: TERRAIN (pure function of x,z) → DECORATE when self+neighbors have terrain;
  setBlock may cross borders. States EMPTY→TERRAIN→DECORATED; render only DECORATED.
- Chunk seed: `splitmix64(seed ^ cx*341873128712 ^ cz*132897987541 ^ phase*GOLDEN)`,
  distinct phase per feature (ores/trees/grass/flowers/cane).
- Oak tree: trunk h = 4+rand(3); leaves 4 layers y=h-3..h: r=2,2,1,1(top); corner
  (|dx|==r&&|dz|==r) skipped 50% (always skipped on top layer); trunk overwrites center;
  leaves only into air. Grass under trunk → dirt.
- Plains tree density ~0.35/chunk: `rand(10)==0 ? 1+rand(2) : (rand(5)==0 ? 1 : 0)`.
  Place only on grass. v1 hack: clamp trunk to local [2,13] to avoid cross-chunk.
- Tall grass: 3-5 patches/chunk, 32 attempts each within ±8 of center, on grass blocks.
  Flowers: 1-2 patch attempts (50%), 8 placements in 7×7, dandelion or poppy per patch.
  Sugar cane: 5-8 attempts; place on grass/sand if a horizontal neighbor of the ground
  block is water; height 2-3.
- Ores (drunken-walk blob replacing stone only): coal 12 attempts/chunk y5-60 size 5-12;
  iron 8 attempts y5-45 size 3-8.
- Caves: skip for v1.

## 3. Player

- Hitbox 0.6×0.6×1.8; eye 1.62 (sneak 1.27); no auto-step of full blocks (step 0.6).
- Per tick: `pos.y += vy; vy = (vy - 0.08) * 0.98` (terminal -3.92/t = 78.4 m/s).
  Jump: vy = 0.42 (+0.2 horizontal boost if sprinting) → 1.25 block jump.
- Horizontal: ground friction ×0.546/t (0.6 slip × 0.91), air ×0.91; accel ground ~0.1
  (×1.3 sprint), air 0.02. (Acceptable simplification: clamp to target speed on ground,
  ~20% control in air.)
- Speeds: walk 4.317, sprint 5.612 (FOV +10%), sneak 1.295 m/s. Sprint = double-tap W
  (within ~7 ticks) or Ctrl; ends on release/collision; needs hunger > 6.
- Water: drag ×0.8/t, gravity 0.02/t, space → +0.04/t up; fall distance resets.
- Sneak prevents walking off edges (clamp so feet AABB overlaps support).
- Controls: WASD, space, shift, mouse (pitch ±90°), scroll/1-9 hotbar, E inventory,
  Q drop, F3 debug, Esc pause/release cursor, F5 third person (optional).

### Targeting / breaking / placing
- Raycast from eye, reach 4.5; Amanatides & Woo DDA; stepped axis = hit face.
- Place at hit+normal if replaceable (air/water/tall grass) and AABB doesn't intersect
  player/entities. Right-click repeats every 4 ticks held.
- Selection box: black ~40% alpha wireframe, inflated 0.002.
- Hardness: leaves 0.2, glass 0.3, dirt/sand 0.5, grass blk 0.6, gravel 0.6, stone 1.5*,
  log/planks 2.0, cobble 2.0*, table 2.5, furnace 3.5*, bedrock ∞. (* = pickaxe required)
- Tool speeds: hand 1, wood 2, stone 4, iron 6 (diamond 8, gold 12).
  `dmg/tick = toolSpeed/(hardness*30)` if harvestable else `1/(hardness*100)`; breaks ≥1.
  Equivalent: time = hardness*1.5s/toolSpeed (harvestable), hardness*5s otherwise.
  Release resets progress. Crack overlay: 10 stages, stage = floor(progress*10).
- Drops: grass→dirt, stone→cobble, leaves→sapling 5%/apple 0.5% (else nothing),
  glass→nothing, stone/cobble w/o pickaxe→nothing, coal ore→coal, iron ore→raw iron
  (needs stone pick+). Item entities: small random velocity, magnet ~1 block, despawn 5 min.

### Inventory / crafting / smelting
- 36 slots (27 + 9 hotbar), stack 64. Left-click move/swap stack, right-click half/one,
  shift-click quick-move.
- Recipes: log→4 planks (shapeless); 2 planks vertical→4 sticks; 4 planks→table;
  8 cobble ring→furnace; coal over stick→4 torches; pickaxe MMM/.S./.S.; axe MM/MS/.S
  (mirrorable); shovel M/S/S; sword M/M/S. M = planks|cobble|iron ingot.
- Furnace: input/fuel/output; 200 ticks/item; coal 1600t (8 items), planks/log 300t,
  stick 100t. Recipes: sand→glass, iron ore→ingot, log→charcoal, raw→cooked meat.
  Fuel ignites only with valid input; progress resets (or -2×) when unlit.

### Health / hunger
- 20 HP. Fall dmg = fallDistance - 3. i-frames 10 ticks (stronger hit deals difference).
- Drowning: 300 ticks air = 10 bubbles (30t each); then 2 HP/s. Refills 2× speed.
- Hunger 20 + saturation; sprint needs >6; regen (1 HP/80t... vanilla 1/4s costing
  exhaustion) needs ≥18; starvation dmg at 0. Exhaustion: sprint/jump/break/regen.
- Death: drop inventory, death screen, respawn at world spawn, full HP.
- Zombie melee: easy 2.5 / normal 3 / hard 4.5.

## 4. Mobs

| Mob | Hitbox W×H | HP | Speed | Drops |
|---|---|---|---|---|
| Zombie | 0.6×1.95 | 20 | ~2.3 m/s | 0-2 rotten flesh |
| Chicken | 0.4×0.7 | 4 | ~2.5 | 0-2 feathers, 1 raw chicken; egg every 6000-12000t |
| Cow | 0.9×1.4 | 10 | ~2.0 | 0-2 leather, 1-3 raw beef |
| Pig | 0.9×0.9 | 10 | ~2.5 | 1-3 raw porkchop |
| Sheep | 0.9×1.3 | 8 | ~2.3 | 1 wool (unsheared) + 1-2 raw mutton |

- Physics = player physics; auto-jump 0.42 on horizontal collision while walking;
  knockback: `v.xz = v.xz/2 + dir*0.4; if onGround v.y = min(0.4, v.y/2+0.4)`;
  red flash + 10-tick i-frames. Chicken: falling vy ×0.6, no fall damage, flaps.
- Passive AI priority: float in water → panic when hurt (run speed ×1.25-2.0 to random
  point 5-9 away, ~5 s) → [sheep] eat grass (1/1000 t, 40t head-down, grass→dirt, regrow
  wool) → wander (1/120 t, random point ±10/±7, walk, idle) → look at player ≤6 blocks
  (2%/t for 40-80t) → random look.
- Zombie: scan every 10t for player ≤16 (vanilla 35); chase: direct-walk at target +
  auto-jump (A* unnecessary); attack ≤2.0 dist, 3 dmg, 20t cooldown; groan every 4-10 s;
  burns in daylight: each second if day && sky-exposed && !inWater → fire 8 s, 1 dmg/s.
- Spawning: hostile attempt ~every 20t: skip if cap (70) or day; pos = random 24-128 from
  player, surface-biased; needs dark + solid below + space; pack 1-4. Despawn >128 inst.,
  >32 1/800 per tick. Passives: worldgen herds 2-4 on grass (weights sheep 12, pig 10,
  chicken 10, cow 8), never despawn, minimal runtime respawn (cap 10).

### Models (16 units = 1 block; box W×H×D; texture-px = unit)
- Zombie (biped, 64×64 tex): head 8×8×8 @y24, body 8×12×4, arms/legs 4×12×4 (arms @y22,
  legs @y12). Arms pitched -90° forward + bob sin(age*0.09)*0.05. Green skin #5B8731,
  teal shirt #00A8A8, purple pants #34345E.
- Pig: head 8×8×8 + snout 4×3×1, body 10×16×8 horizontal, 4 legs 4×6×4. Pink #F0A5A2,
  snout #D8847E.
- Cow: head 8×8×6 + horns 1×3×1, body 12×18×10 + udder, legs 4×12×4. Brown #43342B +
  white patches, horns #B0A8A0.
- Sheep: head 6×6×8, body 8×16×6 + wool overlay inflated ~1.75 (head too), legs 4×12×4.
  Wool #E8E3DC, face/legs #B5917A.
- Chicken: head 4×6×3 + yellow beak 4×2×2 + red wattle 2×2×2, body 6×8×6 horizontal,
  wings 1×4×6, thin yellow legs. White #E8E8E8, beak/legs #F2C14E, wattle #B02020.
- Walk anim: `legA = cos(limbSwing*0.6662)*1.4*amount`, legB phase +π; quadruped diagonal
  pairs share phase; biped arms opposite same-side legs ×0.5; head yaw clamped ±75° vs
  body (body lazily follows); chicken wing flap age*1.0 while falling.

## 5. Save format (planned)
Per-world dir: level.dat (seed, time, player pos/inv/hp/hunger as JSON or NBT-lite),
chunks saved only if modified post-decoration (run-length encoded block array per chunk).
