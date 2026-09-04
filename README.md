# CompanionOS

A **local-first, voice-driven AI desktop companion**. A pixel-art character lives on
your Windows desktop as a transparent always-on-top overlay; you wake it by voice, a
**local** Small Language Model answers, local TTS speaks, and the character animates.
The personality is controlled by a human-editable `persona.md`.

The canonical specification is [`CompanionOS.md`](CompanionOS.md). The active build plan
(milestones, locked tech choices, decisions) lives in the plan file referenced there.

> **Boundary:** the cloud backend only distributes models/assets/updates. The
> conversation — wake word, ASR, SLM, TTS, persona — always runs on your machine.

## Status

Working prototype. A transparent JavaFX overlay renders Lily on the desktop with a calm
"living" idle (breathing, head-tilt, hair-sway, blink), drawn action animations, and
emotion effects. Voice (push-to-talk + Ctrl+Shift+Space), a local Ollama-served SLM (started
on demand), local TTS, opt-in screen awareness, sticky notes, and a chaotic **goose mode**
are wired in.

## Key decisions (see `docs/adr/`)

| Area | Choice |
|------|--------|
| Desktop | Java 21 + JavaFX 21, transparent `Stage`, JNA for Win32 |
| Animation | 8-bit pixel-art, **hybrid** sprite-sheet frames + procedural talk/blink/tint |
| Characters | Built-in packs in V1; user upload + rigging deferred to V2 |
| Local SLM | **Phi-4-mini-instruct** GGUF (no fine-tuning in V1), served by **Ollama** via its OpenAI-compatible API; llama.cpp `llama-server` kept as a fallback |
| Voice | sherpa-onnx (wake word + VAD + ASR + TTS) |
| Backend | Spring Boot modular monolith (Milestone E) |

## Repository layout

```
desktop/     Java desktop app (JavaFX overlay, voice + SLM client, updater)
backend/     Spring Boot control/distribution plane (Milestone E; not yet in build)
ai/          Python tools: sprite generation, GGUF fetch/quantize, eval harness
assets/      Built-in pixel-art character packs (sprites.png + frames.json + persona.md)
benchmarks/  Latency/throughput harnesses (backend, inference, voice, animation)
docs/        architecture / adr / security / research / diagrams
deployment/  docker-compose for local backend dependencies (postgres, minio)
```

## Build & run

Requires JDK 21 (Temurin verified). The Gradle wrapper fetches Gradle + JavaFX/JNA.

```bash
./gradlew :desktop:run
```

**Controls:** drag = reposition, `Ctrl+Shift+Space` = talk by voice (works from any window),
`G` = toggle goose mode, `N` = drop a sticky note, `V` = toggle screen awareness,
`O` = cycle outfit, `1`–`9` = play an action, `Esc` = quit.

## Local SLM (Spike 3)

The character's replies come from a local Phi-4-mini served by [Ollama](https://ollama.com).

```bash
# 1. Get the model GGUF (~2.5 GB) into models/
python ai/tools/fetch_model.py

# 2. Import it into Ollama (reuses the GGUF; no re-download)
ollama create companion-phi4-mini -f models/Phi4Mini.Modelfile

# 3. Smoke-test the local AI loop (no GUI): persona + 3 turns of structured replies
./gradlew :desktop:slm
```

Output is schema-constrained JSON `{speech, emotion, gesture, energy}` (spec §16),
validated before it can drive any behavior (§17). Swap the model by changing
`OLLAMA_MODEL` in `AppConfig`.

