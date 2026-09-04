# ADR-004: 8-bit pixel-art hybrid animation (sprite frames + procedural layer)

Status: Accepted

## Context

The spec (§31–§38) originally proposed a 2D cutout **skeletal** engine: users upload an
arbitrary PNG, place 8–12 anchor points, generate `rig.json`, and retarget generic
skeletal clips. The chosen art direction is **8-bit / pixel-art**, which changes the ideal
technique and the character-authoring model.

## Options

1. **Skeletal cutout + rig.json + retargeting** (original spec) — flexible for arbitrary
   uploaded images; heavy to build (import wizard, retargeting math); looks wrong for
   crisp pixel art (rotating/skewing pixel parts smears them).
2. **Pure sprite-sheet frames** — pre-drawn frames per action; authentic retro motion;
   simplest engine; can't cover fine talk/lip-sync or per-emotion variants without a
   combinatorial explosion of frames.
3. **Hybrid** — sprite-sheet frames for canned actions (idle/walk/sit/sleep/wave/react)
   plus a light **procedural layer** for talk mouth-flap (amplitude lip-sync, §23 V1),
   blink, and per-emotion palette tint composited on top.

## Decision

Option 3 (hybrid). Characters are **built-in pixel-art packs in V1**; user upload +
rigging (the §36 Import Wizard) is **deferred to V2**. The `character/rig` package is
scaffolded but empty. The sprite engine (`SpriteSheet`, `FrameAtlas`, `SpriteAnimator`)
replaces the skeletal classes as the V1 path.

## Trade-offs

- (+) Authentic pixel look; tiny CPU cost; nearest-neighbor integer upscale stays crisp.
- (+) Emotion is independent of base action via tint (WALK+HAPPY) without extra frames.
- (+) Drops the hardest, riskiest spec feature (universal auto/mansual rigging) from V1.
- (−) V1 cannot animate an arbitrary user-supplied photo — only authored pixel packs.
- (−) Reintroducing skeletal/mesh deformation later (V2, §38) is a separate effort.

## Consequences

Local character data is `characters/<name>/{sprites.png, frames.json, persona.md}` instead
of `rig.json` + per-action clip files. Import Wizard, retargeting, and mesh deformation
move to V2.
