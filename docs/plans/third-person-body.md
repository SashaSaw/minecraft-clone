# Plan: Third-person camera + full player body model

## Context

The player is currently **only a hitbox + camera** — `Player` is an `Entity` (0.6×1.8
AABB, yaw/pitch, eye 1.62) and nothing renders any part of it. The camera is built
straight from player position/look in `Game.renderWorld()`, and `MobRenderer` only
draws `Mob` instances (the player isn't one). There is no third-person view.

We want a **third-person perspective** in which the player sees their own full
humanoid body (head, torso, two arms, two legs) animated as they move and look,
holding the current item. This is a sibling feature to the first-person hand
viewmodel on branch `feature/first-person-hand`:

- **First person** → render the hand viewmodel (that branch), no body.
- **Third person (back/front)** → render the full body, no hand viewmodel.

This branch (`feature/third-person-body`) owns the perspective toggle + body model.
At integration the toggle simply selects which renderer runs; the held-item drawing
(block cube / flat sprite) should be factored into a shared helper reused by both.

## Approach

Reuse the existing **box-model machinery in `MobRenderer`** almost verbatim — it
already assembles textured cuboids with pivot + pitch/yaw rotation, body yaw, limb
swing, head tracking, per-face atlas tiles, and lighting. The player is a humanoid
exactly like the zombie model (`MobRenderer.zombie()`), only with arms hanging down
(not stretched forward) and a player skin.

### 1. Perspective state + toggle
- Add a perspective mode to `Game`: `enum View { FIRST, THIRD_BACK, THIRD_FRONT }`,
  cycled by **F5** in `playingEvents()` (mirrors vanilla). Default `FIRST`.
- Autopilot prop `-Dcraft.view=1|3|3f` to set it for headless screenshots (mirrors
  the existing `craft.pos/yaw/pitch/slot` pattern).

### 2. Third-person camera offset (in `Game.renderWorld()`)
The eye/`camPos` and `forward` are already computed (`Game.java:866-882`). For third
person, move the camera origin off the eye along `forward`:
- `THIRD_BACK`: `camOrigin = eye - forward * dist` (look dir unchanged).
- `THIRD_FRONT`: `camOrigin = eye + forward * dist`, looking back (negate forward).
- **Collision clamp:** raycast from the eye outward to `dist` (reuse
  `craft.util.Raycast`) and shorten to the first solid hit minus a small margin, so
  the camera never ends up inside terrain. `dist ≈ 4` blocks.
- Build `view` with `lookAt(camOrigin, eye + lookDir, up)`. `viewRot` (sky) stays
  based on look direction only — unaffected.

### 3. Player body renderer
The player is not a `Mob`, so it needs its own draw path. Two clean options:
- **(Recommended)** Add a `renderPlayer(pv, player, world, dayLight, partial, camPos)`
  method to `MobRenderer`, reusing its private `box()`/`flushMob()`/lighting. It sets
  the same per-mob transform state (`ex/ey/ez`, `bodyYaw*`, `light`) from the player
  and emits a humanoid built like `zombie()` but with **arms down**:
  - head `8×8×8` at pivot (0,24,0), front face = player face, tracks head yaw+pitch
  - body `8×12×4` at (0,12,0)
  - arms `4×12×4` pivots (±6,22,0), swung with `legB/legA` (opposite phase to the
    same-side leg, vanilla style) + the attack swing (see §5)
  - legs `4×12×4` pivots (±2,12,0), swung with `legA/legB`
- (Alternative) a separate `PlayerRenderer` class duplicating the shader/VAO setup —
  rejected: pure duplication of `MobRenderer`'s pipeline.

Call it from `renderWorld` in the **mob pass** (world-space, normal depth — no depth
clear), right after `mobRenderer.render(...)` (`Game.java:911`), guarded by
`view != FIRST`.

### 4. Walk animation state on `Player`
`MobRenderer` drives limbs from `Mob.limbSwing`/`limbSwingAmount`. `Player` lacks
these. Add them and update per tick from horizontal speed, copying `Mob`'s formula:
```
double hs = Math.hypot(x - prevX, z - prevZ);
limbSwingAmount += (Math.min(hs * 4, 1) - limbSwingAmount) * 0.4;
limbSwing += hs;
```
Body yaw = movement direction when moving, else look yaw (or, simplest first pass:
body yaw = look yaw, head adds only pitch). Head yaw/pitch from `player.yaw/pitch`.

### 5. Held item + attack swing in third person
- Reuse `player.swingTicks` (already added on the hand branch; re-add here, or pull
  it in when the branches merge) to add a forward arm-swing to the **right arm** when
  attacking/placing.
- Render the held item in the right hand: a small 3D cube for blocks (block face
  tiles) or a flat sprite for tools/food — the **same logic as `HandRenderer`**.
  Factor this into a shared helper (e.g. `HeldItemModel`) at integration so both the
  first-person hand and the third-person body draw items identically.

### 6. Player skin tiles
Add 4 procedurally-drawn tiles to `TextureGen` + `Tiles` constants at **free atlas
indices 96–99** (mob skins end at 95; index 78 is taken by the hand branch's
`ARM_SKIN`, so 96+ avoids any merge collision):
`PLAYER_FACE`, `PLAYER_SKIN`, `PLAYER_SHIRT`, `PLAYER_PANTS` — a simple Steve-like
palette (skin tone, cyan/teal shirt, blue pants, eyes on the face tile, reusing the
existing `eyes()` helper).

## Files

- **New:** `src/main/java/craft/render/MobRenderer.java` → add `renderPlayer(...)` +
  a `playerModel(...)` builder (or a new `render/PlayerRenderer.java` if preferred).
- `src/main/java/craft/Game.java` — `View` enum + F5 toggle + `-Dcraft.view` prop +
  third-person camera offset with collision clamp + invoke the player renderer.
- `src/main/java/craft/player/Player.java` — `limbSwing`/`limbSwingAmount` (+ reuse
  `swingTicks` when merged with the hand branch); update in `tick`.
- `src/main/java/craft/render/TextureGen.java` + `Tiles.java` — player skin tiles
  (indices 96–99).
- (At integration) shared held-item helper used by both `HandRenderer` and the body.

## Verification

1. Fresh install: `gradle installDist`.
2. Headless screenshots via autopilot (`-Dcraft.view`, `-Dcraft.demo=world` fills the
   inventory, `-Dcraft.slot` chooses the held item):
   ```
   java -XstartOnFirstThread -Dcraft.shot=/tmp/body-back.png -Dcraft.shotDelay=10 \
     -Dcraft.exitAfter=13 -Dcraft.demo=world -Dcraft.view=3 -Dcraft.slot=0 \
     -cp "build/install/minecraft/lib/*" craft.Main 777
   ```
   - `view=3` (back): full body visible from behind, head tracks pitch.
   - `view=3f` (front): body faces the camera.
   - Confirm held block shows as a cube in the hand; a tool shows as a sprite.
3. Manual run (`gradle run`): F5 cycles First → Third-back → Third-front; walking
   animates the legs/arms; attacking swings the right arm; the camera pulls in when
   backing against a wall (collision clamp) instead of clipping through it.

## Branch / integration notes

- Branches are siblings off `master`:
  `feature/first-person-hand` (committed) and `feature/third-person-body` (this one).
- Final integration: a single `View` state selects hand (FIRST) vs body (THIRD_*).
  Expect a small merge in `Game.renderWorld()` (both touch the post-mob render
  section) and in `Player` (both add swing/animation fields). Extract the held-item
  cube/sprite emit into one shared helper so first- and third-person stay consistent.
