# Lily — Animation Frame Generation Brief (for an image-generation agent)

> Give the **PROMPT** section below to a pixel-art image-generation agent verbatim.
> Reference images to attach: `assets/characters/lily/lily-image.png` (character likeness) and
> `assets/characters/lily/sprites.png` (exact palette / pixel style).

---

## PROMPT (give verbatim)

### Role & goal
You are a pixel-art game animator. Produce a complete set of **2D pixel-art animation frames** for a
desktop-mascot character named **Lily**, to be played back by a sprite engine. The frames will be
packed into sprite sheets where **each animation is one horizontal strip (a row) and each frame is a
cell of identical size**. Absolute frame-to-frame consistency and registration are the top priority —
this is animation, not concept art.

### Character bible — "Lily" (match the reference images exactly)
A cute anime "VTuber" bunny-girl in **pixel-art** style, pastel-pink kawaii aesthetic. Use the
provided reference image for likeness and the provided cutout for the exact palette and pixel style.
- **Hair:** very long, wavy, pastel-pink twin-tail hair flowing past the waist; small heart hairclips
  and a little ahoge (cowlick).
- **Ears:** white rabbit ears with soft-pink inner and a tiny heart accent.
- **Face:** large expressive magenta/pink eyes with white highlights; soft pink blush; small friendly
  smile; light lineart.
- **Outfit:** white frilly leotard-dress with pink heart accents and ribbon lacing; a pink bow at the
  chest; a dark choker with a small pendant + pink beaded necklace.
- **Over it:** an oversized fluffy pastel-pink off-shoulder cardigan/sweater with heart patterns and
  long "sweater-paw" sleeves; a pink frilly peplum/mini-skirt with bows.
- **Legs:** white thigh-high stockings with pink bows and light fishnet accents; small white shoes.
- **Palette:** pastel pink, white, magenta accents, dark-plum outlines. Keep it identical in every
  frame.

### Global technical requirements (apply to EVERY frame — non-negotiable)
1. **Transparent background** (alpha PNG). No background, no scenery, no ground, no baked drop-shadow,
   no text, no UI, no signature/watermark, no frame borders.
2. **Consistent camera & framing:** front-facing three-quarter view, **full body**, character
   centered horizontally, **feet resting on the same baseline** near the bottom of the cell in every
   frame. Same camera distance and **same character scale/height** across all frames and all actions
   (≈85% of cell height). Do NOT zoom, crop differently, or change the head size between frames.
2b. **Registration:** the character must sit at the same position/pivot in every cell so the animation
   does not jitter. Only the parts the action calls for should move; everything else stays put.
3. **Uniform cell size:** one square cell size for all frames (e.g. **512×512** working resolution;
   deliver all cells at this exact size). Within an action all frames share the cell and registration.
4. **Style:** clean **pixel-art**, crisp pixels (nearest-neighbor friendly), limited pastel palette,
   clean dark outlines, soft cel shading; consistent soft top-down lighting; **no motion blur**,
   no anti-aliased fringe on the silhouette (hard alpha edge so it cuts out cleanly).
5. **One consistent character:** same face, proportions, outfit, and colors in every single frame.
   Prefer an image-to-image / character-consistent workflow seeded from the reference image; keep a
   fixed character description; vary only the pose. If pose control (OpenPose/ControlNet) is
   available, use it to guarantee clean, believable poses.
6. Motion should read as **natural, cute, and lively** (gentle secondary motion is encouraged and must
   be consistent across the loop): hair/ears sway, skirt bounce, occasional blush/sparkle — but never
   at the cost of character or registration consistency.

### Animations to produce
Deliver each as an ordered frame sequence. Frame counts are minimums; smooth in-betweens welcome.
Loop = the last frame flows back into the first.

**A. Core (highest priority)**
- `idle` — 4 frames, **loop**, ~4 fps. Standing relaxed, gentle breathing (chest/shoulders rise & fall,
  tiny vertical bob), subtle hair/ear sway. Eyes open, soft smile.
- `blink` — 3 frames, non-loop. Same idle pose: eyes open → half-closed → fully closed (then engine
  returns to idle). Keep everything else identical to idle frame 0.
- `talk` — 5 frames, **loop**, ~12 fps. Head/body steady; ONLY the mouth changes across frames so the
  engine can pick a frame by voice loudness: (1) closed, (2) slightly open, (3) mid "ah", (4) wide
  "oh", (5) mid. Eyes open.
- `walk` — 8 frames, **loop**, ~10 fps. Front-facing **walk-in-place** cycle: legs alternate stepping,
  arms swing gently, body bobs up/down, hair and ears bounce. Must tile seamlessly. (Engine mirrors it
  horizontally for walking left, so keep it symmetric/front-facing.)

**B. Gestures**
- `wave` — 6 frames, non-loop, ~12 fps. Raise one arm from the side up beside the head and wave the
  hand (2–3 wave positions), cheerful expression; last frame settles toward idle.
- `nod` — 3 frames, non-loop. Head neutral → dips down → returns (a friendly "yes").
- `sit` — 2 frames, **loop**, ~3 fps. Sitting on the ground, legs folded to one side, relaxed; gentle
  breathing between the 2 frames.
- `sleep` — 4 frames, **loop**, ~3 fps. Sitting/curled, eyes closed, head tilted, slow breathing, a
  floating "Zzz" that grows/drifts across the frames.

**C. Emotions (drive from the character's mood)**
- `happy` — 4 frames, non-loop, ~10 fps. Excited little hop with a big smile, sweater-paws up, sparkle
  eyes / small hearts.
- `sad` — 3 frames, non-loop. Droopy posture, downcast eyes, a small teardrop; slow and gentle.
- `angry` — 3 frames, non-loop. Puffed cheeks + frown + angry brows, small fists / arms crossed, a
  tiny shake (offset the body a few px between frames), maybe an anger vein mark.
- `surprised` — 3 frames, non-loop, fast. Startled: wide eyes, a small hop back, sweater-paws up, a
  "!" mark above the head.

**D. Goose-style desktop interaction**
- `grab_cursor` — 4 frames, non-loop. Leaning/reaching one arm out to the side toward an (implied)
  mouse cursor with a mischievous grin; a small "tug" motion at the end.
- `held_drag` — 3 frames, **loop**. Character dangling as if picked up/held by the mouse (arms, legs,
  hair and ears hanging & swaying), dizzy/surprised face; gentle sway loop.
- `push_window` — 4 frames, non-loop. Turned slightly to one side, pushing/shoving with both hands
  against an (implied, off-frame) edge, effort face, feet braced; a small "heave" cycle.
- `sit_on_edge` — 2 frames, **loop**, ~3 fps. Sitting as if perched on a ledge/window edge, legs
  dangling and swinging gently, relaxed happy face.

> Creative freedom: you may add small tasteful cute idle extras (an occasional ear twitch, a floating
> heart, a tail wag) as long as they stay consistent within a loop and never break registration or the
> character design.

### Output & packaging
- Preferred: for **each** action, output **one horizontal strip PNG** with all its frames in order,
  evenly spaced, transparent background, every frame in an identically sized cell with the character
  registered identically (same feet baseline & center in each cell). Name it `<action>_strip.png`
  (e.g. `walk_strip.png`).
- ALSO useful: individual transparent frames named `<action>_00.png, <action>_01.png, …` at the same
  cell size.
- First produce a small **character reference/turnaround** (front idle), lock the design, then
  generate every action from that locked design so all sheets match.
- Report, per action: frame count, intended fps, and whether it loops.

### Negative prompt / avoid
`background, scenery, floor, ground shadow, drop shadow, text, watermark, signature, logo, UI, frame,
border, multiple characters, inconsistent outfit, changing hairstyle, changing proportions, off-model
face, blurry, motion blur, anti-aliased soft edges on silhouette, cropped body, cut-off feet, extra
limbs, jpeg artifacts, 3D render, realistic photo`

---

## Mapping back to CompanionOS (for integration)
- Each action strip → one **row** in `assets/characters/lily/sprites.png`; fill `frames.json`
  `actions` with `{row, frames, fps, loop}` — existing format
  (`desktop/src/main/java/os/companion/character/animation/{FrameAtlas,SpriteAnimator,SpriteSheet}.java`).
- With drawn `talk`/`blink` frames, the procedural mouth/eye overlay in `CharacterOverlay` can be
  disabled for this character (the `mouth`/`eyes` regions become optional).
- `character/behaviour/BehaviourEngine.java` + `MovementController.java` already map gestures/emotions
  to action names; new rows plug in by name.
- A small Python/PIL packer can assemble the per-action strips into one `sprites.png` grid and emit the
  matching `frames.json`.
