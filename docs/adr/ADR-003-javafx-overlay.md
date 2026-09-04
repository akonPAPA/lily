# ADR-003: JavaFX transparent overlay for the desktop character

Status: Accepted

## Context

The character must appear to live on the desktop: no window frame, transparent
background, always-on-top, movable across the screen (spec §25–§27). The app is Java.

## Options

1. **JavaFX transparent `Stage`** — `StageStyle.TRANSPARENT`, `Scene` fill
   `Color.TRANSPARENT`, small window tracking the character bounds; move the `Stage`
   itself across the desktop.
2. **Swing/AWT `JWindow`** with per-pixel translucency — older toolkit, weaker animation
   and rendering story.
3. **Native per-OS overlay** (Win32 layered window directly) — most control, most native
   code, least portable.

## Decision

Option 1: a small transparent, frameless, always-on-top JavaFX `Stage` moved across the
desktop (not a fullscreen overlay in V1). A thin JNA/Win32 bridge (ADR — nativeplatform)
fills platform gaps: enforcing topmost Z-order and, later, click-through and monitor
geometry (spec §27).

## Trade-offs

- (+) Single toolkit for rendering + animation + input; `Canvas` gives cheap 2D pixel
  drawing with `imageSmoothing=false` for crisp sprites.
- (+) Portable to macOS later with a different native bridge.
- (−) Transparent-window behavior and always-on-top are not fully portable — hence the
  JNA reinforcement on Windows.
- (−) All rendering is on the JavaFX Application Thread, so inference/voice/IO must never
  run there (spec §29/§30).

## Consequences

Rendering is a JavaFX `AnimationTimer` loop; the window position equals the character's
world X/Y. Worker threads communicate via an event bus/queues (spec §30).
