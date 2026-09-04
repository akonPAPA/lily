# CompanionOS

## 0. Project Status

**Document type:** Canonical project specification  
**Project name:** CompanionOS  
**Primary goal:** Build a local-first AI desktop companion that exists as an animated character on Windows/macOS, communicates primarily by voice, uses a locally running fine-tuned Small Language Model (SLM), and can be customized through a human-editable `persona.md` file.

This document defines the project idea, architecture, data flows, implementation boundaries, research directions, engineering requirements, and a practical development roadmap.

---

# 1. Core Idea

CompanionOS is a desktop AI-character platform where the user can:

1. Install the application on Windows or macOS.
2. Download a compatible local AI model and required runtime assets.
3. Upload or select a character image.
4. Prepare a character rig for that image.
5. Make the character appear as a transparent desktop overlay.
6. Let the character walk, idle, react, talk, sleep, wave, and change emotional state.
7. Speak to the character naturally using a microphone.
8. Trigger the character through a wake word or key phrase such as:
   - `Hi Lily`
   - `Hey Alice`
   - `Hello <character-name>`
9. Receive spoken responses through local TTS.
10. Customize the character's speech, personality, tone, mannerisms, and behavioral preferences by editing one local `persona.md` file.
11. Run the language model, wake-word detection, speech recognition, conversation processing, and TTS locally.
12. Use the backend only as a control/distribution plane for:
   - authentication,
   - application updates,
   - model manifests,
   - model downloads,
   - voice-model downloads,
   - animation packs,
   - asset manifests,
   - release channels,
   - integrity/signature metadata.

The backend is **not responsible for the conversation itself**.

Normal user speech is not sent to the backend.

---

# 2. Product Principle

CompanionOS is **local-first**.

The fundamental rule is:

```text
Conversation = local
SLM inference = local
Wake word = local
VAD = local
ASR = local
TTS = local
persona.md = local
recent context = local
animation = local
character rendering = local
```

The server should only be needed for installation, updates, downloads, optional authentication, and release distribution.

After installation and model download, the companion should remain usable offline.

---

# 3. What CompanionOS Is Not

CompanionOS is not:

- a ChatGPT clone;
- a text-chat application;
- a cloud LLM wrapper;
- a server-side personalization platform;
- a per-user fine-tuning SaaS;
- a full autonomous desktop-control agent;
- a full kernel-level EDR-like desktop process;
- a 3D game engine;
- a video generation system;
- an 8B–12B pretraining project;
- a project where every conversation triggers gradient updates.

The core interaction is **voice-first**.

There may be a settings UI, logs/debug UI, and administrative views, but the normal conversational experience does not require a chat window.

---

# 4. Main User Experience

## 4.1 First launch

```text
Install CompanionOS
        ↓
Launch
        ↓
Login / offline mode
        ↓
Hardware detection
        ↓
CPU / RAM / GPU / OS inspection
        ↓
Select recommended local model
        ↓
Download model + voice assets + animation runtime
        ↓
Verify checksums/signatures
        ↓
Install atomically
        ↓
Create/select character
        ↓
Configure persona.md
        ↓
Start Companion
```

---

## 4.2 Character creation

User either:

- chooses a built-in character;
- uploads a transparent full-body PNG;
- imports a prepared character package.

For V1, uploaded images should preferably be:

- full body;
- front-facing;
- transparent background;
- visually separable arms/legs;
- no extreme perspective;
- not heavily occluded.

Flow:

```text
Upload PNG
    ↓
validate format
    ↓
display Character Import Wizard
    ↓
select anchor/joint points
    ↓
generate rig.json
    ↓
attach generic animation pack
    ↓
preview
    ↓
save character package
```

---

# 5. Main Runtime Interaction

The normal user interaction:

```text
Character is idle on desktop
        ↓
microphone continuously monitored by tiny wake-word detector
        ↓
user says: "Hi Lily"
        ↓
wake word detected
        ↓
Voice Activity Detection starts utterance capture
        ↓
user asks something
        ↓
silence detected
        ↓
local ASR converts speech to text
        ↓
Context Builder loads:
    - runtime rules
    - persona.md
    - recent local conversation turns
    - current character state
        ↓
local fine-tuned SLM generates a structured response
        ↓
response parser validates schema
        ↓
speech goes to local TTS
emotion/gesture goes to Behaviour Engine
        ↓
character speaks
        +
character reacts/animates
        ↓
return to passive listening
```

---

# 6. Conversation State Machine

```text
PASSIVE_LISTENING
        │
        │ wake word
        ▼
ACTIVE_LISTENING
        │
        │ end-of-speech
        ▼
TRANSCRIBING
        │
        ▼
THINKING
        │
        ▼
SPEAKING
        │
        ▼
PASSIVE_LISTENING
```

Additional states:

```text
SUSPENDED
ERROR
DOWNLOADING_MODEL
UPDATING
MODEL_UNAVAILABLE
MICROPHONE_UNAVAILABLE
```

---

# 7. Barge-In / Interruption

The user must be able to interrupt the character.

Example:

```text
Character:
"I think what you should do is—"

User:
"Lily, stop."
```

Required flow:

```text
SPEAKING
    ↓
wake word / speech interrupt detected
    ↓
cancel TTS stream
    ↓
cancel remaining speech buffer
    ↓
cancel current non-critical gesture
    ↓
transition to ACTIVE_LISTENING
```

This feature is important for making the assistant feel conversational rather than like a voice recorder.

---

# 8. Persona Customization

The user does not fine-tune the model.

The user customizes the character through:

```text
persona.md
```

This is runtime conditioning.

Example:

```markdown
# Identity

Name: Lily
Role: Desktop companion

# Personality

Playful, energetic, sarcastic, but friendly.

# Speech Style

- Speak informally.
- Prefer short natural responses.
- Use British street slang occasionally.
- Avoid sounding corporate or formal.
- Tease the user sometimes.

# Relationship

Treat the user as a close friend.

# Behaviour

When happy:
- sound more energetic
- prefer wave or bounce reactions

When annoyed:
- respond more briefly
- use dry sarcasm

# Voice

Preferred energy: medium
Preferred speed: 1.05
Preferred pitch: slightly low

# Restrictions

- Do not produce long lectures unless explicitly asked.
- Do not pretend to perform external actions that were not performed.
- Do not claim access to the operating system beyond the capabilities provided by CompanionOS.
```

---

# 9. Persona System Design

The model should be trained to follow arbitrary persona specifications.

The target capability is:

```text
persona specification
        +
conversation context
        +
user utterance
        ↓
natural persona-consistent response
```

The model should not be trained as only one fixed personality.

It should learn **persona-conditioned dialogue generation**.

---

# 10. Local AI Architecture

```text
                 LOCAL AI ENGINE

persona.md ───────────────┐
runtime rules ────────────┤
recent turns ─────────────┤
user ASR text ────────────┤
character state ──────────┘
                         ↓
                  Context Builder
                         ↓
                  Local SLM Runtime
                         ↓
                Structured Generation
                         ↓
                  Schema Validator
                   ┌─────┴─────┐
                   ▼           ▼
                 speech     behaviour
                   │           │
                   ▼           ▼
                  TTS    Behaviour Engine
```

---

# 11. Local Model Size

Target model family:

**roughly 2B–4B parameters**, depending on quality.

The project should not prematurely lock itself to one model.

The real requirement is:

> Select the smallest model that still delivers acceptable conversational quality, persona adherence, multilingual performance, controllability, and latency after fine-tuning and quantization.

Candidate classes to benchmark:

- 2B class;
- 3B class;
- 4B class.

Target deployment:

- Q4;
- optionally Q5;
- potentially Q8 for high-memory systems.

---

# 12. Model Selection Metrics

Candidate SLMs should be evaluated on:

## Conversational quality

- naturalness;
- response relevance;
- short conversational responses;
- ability to avoid generic assistant language;
- conversational continuity.

## Persona behavior

- persona adherence;
- style adherence;
- slang control;
- emotional consistency;
- tone control.

## Language capability

- English;
- Russian;
- code-switching;
- mixed Russian/English conversation.

## Safety / correctness

- hallucination rate;
- instruction conflicts;
- prompt injection resistance where applicable;
- invalid structured output rate.

## Runtime

- model file size;
- RAM;
- VRAM;
- first-token latency;
- tokens/sec;
- CPU inference viability;
- GPU inference viability;
- context-size degradation.

---

# 13. Fine-Tuning Pipeline

The model distributed to users should be fine-tuned beforehand.

```text
Base SLM
   ↓
Dataset collection
   ↓
Cleaning
   ↓
Deduplication
   ↓
Language balancing
   ↓
Style/persona labeling
   ↓
Quality scoring
   ↓
Safety filtering
   ↓
SFT dataset
   ↓
LoRA / QLoRA fine-tuning
   ↓
Candidate model
   ↓
Evaluation
   ↓
Regression tests
   ↓
Quantization
   ↓
Q4 / Q5 deployment candidate
   ↓
Runtime benchmarks
   ↓
Final release
```

---

# 14. Fine-Tuning Objectives

Fine-tuning is aimed at the CompanionOS workload.

Priority:

```text
natural conversation
+
persona adherence
+
instruction following
+
short latency-friendly responses
+
style controllability
+
multilingual speech
+
emotional consistency
```

It is not necessary for the model to be the strongest available system on:

- advanced mathematics;
- coding benchmarks;
- general knowledge;
- large-scale reasoning;
- MMLU-like tests.

A model can be weaker at those tasks but still be a better CompanionOS model.

---

# 15. Example Training Format

```text
PERSONA:
Talk like a young British street guy.
Be informal, sarcastic and direct.

USER:
Should I go outside today?

ASSISTANT:
Mate, you've been staring at that screen for half the day.
Yeah. Go touch some grass.
```

Another sample:

```text
PERSONA:
Cute, shy, slightly awkward character.
Speak softly and briefly.

USER:
Morning.

ASSISTANT:
M-morning... you're up earlier than usual today.
```

The training task is therefore:

```text
persona + dialogue → correct persona-conditioned answer
```

---

# 16. Structured SLM Output

The SLM should not directly control the renderer or the operating system.

The model produces semantic intent.

Example:

```json
{
  "speech": "Oi, you're finally awake.",
  "emotion": "AMUSED",
  "gesture": "WAVE",
  "energy": 0.72
}
```

Supported enums can be limited:

```text
Emotion:
NEUTRAL
HAPPY
SAD
ANGRY
AMUSED
CONFUSED
EXCITED
TIRED

Gesture:
NONE
WAVE
NOD
SHAKE_HEAD
SHRUG
BOUNCE
LOOK_AWAY
SIT
```

Unknown values are rejected or replaced by safe defaults.

---

# 17. AI Security Boundary

Never allow:

```text
LLM
 ↓
arbitrary native command
```

Instead:

```text
LLM
 ↓
structured semantic intent
 ↓
schema validation
 ↓
BehaviourEngine / PolicyEngine
 ↓
known safe action
```

If future versions add tools:

```text
LLM
 ↓
tool proposal
 ↓
policy validation
 ↓
authorization
 ↓
argument validation
 ↓
execution
```

---

# 18. Voice Architecture

```text
MICROPHONE
    ↓
PCM stream
    ↓
Wake Word Detector
    ↓
VAD
    ↓
ASR
    ↓
Text
    ↓
Context Builder
    ↓
SLM
    ↓
TTS
    ↓
Speaker
```

All of this runs locally.

---

# 19. Wake Word

Wake-word detection should be a tiny always-on model.

Do not use a full ASR model continuously.

Example key phrases:

```text
Hi Lily
Hey Lily
Hello Lily
Lily
```

Future versions may allow configurable names.

Wake-word runtime requirements:

- very low CPU usage;
- low memory;
- continuous listening;
- low false-positive rate;
- acceptable false-negative rate;
- configurable sensitivity.

---

# 20. Voice Activity Detection

After wake word:

```text
wake detected
   ↓
record speech
   ↓
VAD identifies speech boundaries
   ↓
stop capture after sufficient silence
```

Important parameters:

- speech onset threshold;
- minimum speech duration;
- silence duration;
- maximum utterance duration;
- interruption handling.

---

# 21. ASR

ASR must run locally.

Candidate backend runtimes should be benchmarked on:

- word error rate;
- Russian;
- English;
- mixed-language speech;
- latency;
- CPU load;
- GPU load;
- memory;
- streaming capability.

Potential runtime directions include:

- sherpa-onnx ecosystem;
- whisper.cpp;
- ONNX Runtime-based ASR.

The project should choose based on measurements rather than preference.

---

# 22. TTS

TTS should also run locally.

Target properties:

- low latency;
- human-sounding voice;
- emotional variance;
- configurable voice;
- potentially multiple voice packs;
- support for interruption;
- streaming output where possible.

The server may distribute TTS models, but synthesis should run locally.

---

# 23. Lip Sync

## V1

Use audio amplitude.

For short audio windows:

```text
RMS audio energy
       ↓
mouth openness
```

Simple approximation:

```text
low RMS  → closed mouth
high RMS → open mouth
```

## V2

Use phoneme/viseme alignment.

```text
TTS phonemes
   ↓
viseme mapper
   ↓
mouth shape animation
```

Example:

```text
M/B/P → closed lips
AA    → open
O     → rounded lips
F/V   → lower lip / upper teeth
```

---

# 24. Desktop Character Architecture

```text
CharacterController
      │
      ├── BehaviourEngine
      │
      ├── MovementController
      │
      ├── EmotionState
      │
      ├── AnimationMixer
      │
      └── CharacterRenderer
```

---

# 25. Windows Rendering Concept

The character is not rendered “inside Windows”.

It is rendered in a **transparent desktop window**.

Concept:

```text
Windows Desktop
      +
transparent top-level window
      +
character renderer
```

Windows only sees:

> a normal window with transparent pixels.

The CompanionOS runtime handles:

- bones;
- poses;
- animation;
- interpolation;
- character movement;
- reactions;
- speech animation.

---

# 26. Windows Overlay

For the first implementation:

- JavaFX;
- transparent `Stage`;
- no frame;
- always-on-top;
- small window approximately matching character bounds;
- move the window itself across the desktop.

Do not use a fullscreen overlay for V1.

Concept:

```text
Desktop

                    ┌────────────┐
                    │ Character  │
                    │ Window     │
                    └────────────┘
```

The rest of the desktop remains fully normal.

---

# 27. Native Windows Integration

A small JNA/Win32 bridge may be used for:

- window handle access;
- always-on-top enforcement;
- monitor bounds;
- work area detection;
- window geometry;
- optional click-through behavior;
- future awareness of other windows.

Possible native functions:

```text
SetWindowPos
GetWindowLongPtr
SetWindowLongPtr
GetWindowRect
EnumWindows
MonitorFromWindow
GetMonitorInfo
```

JavaFX should be used wherever sufficient; Win32 is only for platform-specific gaps.

---

# 28. Desktop Movement

Character world state:

```text
x
y
velocityX
velocityY
direction
movementState
```

Per-frame update:

```text
x = x + velocityX × deltaTime
y = y + velocityY × deltaTime
```

If screen boundary reached:

```text
reverse direction
switch animation direction
```

For V1, movement can mostly happen along a “floor” near the bottom of the monitor.

---

# 29. Render Loop

GUI rendering should run on the JavaFX Application Thread.

Concept:

```text
AnimationTimer
    ↓
deltaTime
    ↓
update CharacterState
    ↓
sample active animations
    ↓
blend animation layers
    ↓
render character
    ↓
move Stage if needed
```

Never run SLM inference on the JavaFX thread.

---

# 30. Thread Architecture

```text
JavaFX Application Thread
    └── rendering / UI only

Audio Capture Thread
    └── microphone PCM

Wake/VAD Worker
    └── KWS + VAD

ASR Worker
    └── speech recognition

SLM Worker
    └── local inference

TTS Worker
    └── audio synthesis

Network Worker
    └── manifests/download metadata

Download Worker
    └── large model/assets downloads
```

Communication should happen through:

- immutable events;
- queues;
- futures;
- bounded buffers;
- state machines.

---

# 31. Animation Runtime

The goal is to build a small reusable 2D animation engine rather than playing GIF files.

Core concepts:

```text
Skeleton
Bone
Rig
AnimationClip
AnimationTrack
Keyframe
AnimationPlayer
AnimationMixer
AnimationLayer
```

---

# 32. Character Skeleton

Example:

```text
              head
               │
              neck
               │
       ┌───── torso ─────┐
       │                 │
 leftUpperArm       rightUpperArm
       │                 │
 leftForearm        rightForearm
       │                 │
     leftHand          rightHand

              hips
             /    \
      leftThigh      rightThigh
          │              │
       leftCalf       rightCalf
          │              │
       leftFoot       rightFoot
```

---

# 33. rig.json

`rig.json` maps the uploaded character image to a skeleton.

Example:

```json
{
  "bones": {
    "root": {"x": 250, "y": 620},
    "hips": {"x": 250, "y": 430},
    "torso": {"x": 250, "y": 290},
    "head": {"x": 250, "y": 130},

    "leftShoulder": {"x": 190, "y": 260},
    "leftElbow": {"x": 145, "y": 350},
    "leftHand": {"x": 125, "y": 430},

    "rightShoulder": {"x": 310, "y": 260},
    "rightElbow": {"x": 350, "y": 350},
    "rightHand": {"x": 380, "y": 420}
  }
}
```

---

# 34. Animation Pack

Generic animation data should be independent of a specific character.

Example:

```json
{
  "name": "walk",
  "durationMs": 800,
  "loop": true,

  "tracks": {
    "leftThigh.rotation": [
      [0, 18],
      [400, -18],
      [800, 18]
    ],

    "rightThigh.rotation": [
      [0, -18],
      [400, 18],
      [800, -18]
    ],

    "torso.positionY": [
      [0, 0],
      [200, -4],
      [400, 0],
      [600, -4],
      [800, 0]
    ]
  }
}
```

---

# 35. Animation Retargeting

The generic motion:

```text
walk.anim.json
```

is applied to:

```text
character-specific rig.json
```

Flow:

```text
generic skeletal motion
        +
character rig
        ↓
retargeting
        ↓
character-specific pose
```

This avoids storing one complete animation per character.

---

# 36. Character Import Wizard

V1 should not attempt universal automatic rigging.

User selects approximately 8–12 anchor points:

- head;
- neck;
- shoulders;
- elbows;
- hands;
- hips;
- knees;
- feet.

Then CompanionOS generates:

```text
rig.json
```

Later versions may use local pose estimation to predict these automatically.

---

# 37. V1 Animation Technique

Recommended V1:

## 2D cutout skeletal animation

Character can be represented as parts:

```text
head
torso
left upper arm
left forearm
right upper arm
right forearm
hips
left thigh
left calf
right thigh
right calf
```

Each part follows a bone transform.

Advantages:

- simple;
- explainable;
- fast;
- low CPU/GPU cost;
- easy to debug;
- achievable in a short development cycle.

---

# 38. V2 Animation Technique

## Mesh deformation / skinning

Image is represented by a triangular mesh.

Each vertex has bone weights.

Concept:

```text
v' = Σ(w_i × M_i × v)
```

Where:

- `v` = source vertex;
- `w_i` = bone weight;
- `M_i` = bone transformation matrix.

Benefits:

- smoother deformation;
- fewer visible cutout seams;
- more natural body bending.

This is a later improvement.

---

# 39. Animation States

Primary movement states:

```text
IDLE
WALK
SIT
SLEEP
TALK
REACT
```

Emotional states:

```text
NEUTRAL
HAPPY
SAD
ANGRY
AMUSED
CONFUSED
EXCITED
TIRED
```

These should be independent.

Example:

```text
WALK + HAPPY
TALK + ANNOYED
SIT + TIRED
```

---

# 40. Animation Layers

Animations can be composed.

Example:

```text
BASE LAYER:
WALK

UPPER BODY LAYER:
WAVE

FACE LAYER:
HAPPY

TALK LAYER:
MOUTH
```

Result:

```text
walk + wave + smile + lip sync
```

---

# 41. Animation Interpolation

Between keyframes:

```text
A = 10°
B = 30°
```

Use interpolation.

Initial implementation:

```text
linear interpolation
```

Later:

- ease-in;
- ease-out;
- cubic interpolation;
- Bézier curves.

---

# 42. Behaviour Engine

The Behaviour Engine translates semantic intentions into actual animations.

Input:

```text
emotion = AMUSED
gesture = WAVE
speechActive = true
```

Output:

```text
activate:
- base idle/walk state
- upper-body wave
- amused face state
- talk mouth animation
```

The SLM does not know bone coordinates.

---

# 43. Optional Window Awareness

V1:

- character walks along desktop floor;
- no deep awareness of applications.

V2:

- detect foreground window;
- read window rectangles;
- avoid overlapping important UI;
- potentially stand/sit on window edges.

V3:

- use OS accessibility/UI automation APIs only after explicit permissions.

Full autonomous computer control is not a V1 requirement.

---

# 44. Local File Structure

Example:

```text
CompanionOS/
│
├── app/
│
├── models/
│   └── companion-slm-q4.gguf
│
├── speech/
│   ├── kws/
│   ├── vad/
│   ├── asr/
│   └── tts/
│
├── characters/
│   └── lily/
│       ├── character.png
│       ├── persona.md
│       ├── rig.json
│       └── character.json
│
├── animations/
│   └── v1/
│       ├── idle.anim.json
│       ├── walk.anim.json
│       ├── sit.anim.json
│       ├── wave.anim.json
│       ├── talk.anim.json
│       ├── happy.anim.json
│       └── sleep.anim.json
│
├── runtime/
│   ├── conversation-context.json
│   ├── install-state.json
│   └── settings.json
│
└── logs/
```

---

# 45. Java Desktop Module Structure

```text
companion-desktop/
│
├── app/
│   ├── CompanionApplication
│   ├── LifecycleManager
│   └── EventBus
│
├── character/
│   ├── Character
│   ├── CharacterState
│   ├── CharacterController
│   │
│   ├── rig/
│   │   ├── Skeleton
│   │   ├── Bone
│   │   ├── Rig
│   │   └── RigLoader
│   │
│   ├── animation/
│   │   ├── AnimationClip
│   │   ├── AnimationTrack
│   │   ├── Keyframe
│   │   ├── AnimationPlayer
│   │   ├── AnimationMixer
│   │   └── AnimationRepository
│   │
│   ├── behaviour/
│   │   ├── BehaviourEngine
│   │   ├── EmotionState
│   │   ├── MovementState
│   │   └── Gesture
│   │
│   └── rendering/
│       ├── CharacterRenderer
│       └── CharacterOverlay
│
├── ai/
│   ├── LocalModel
│   ├── InferenceEngine
│   ├── ContextBuilder
│   ├── PersonaLoader
│   ├── ResponseSchema
│   └── ModelResponseParser
│
├── voice/
│   ├── MicrophoneCapture
│   ├── WakeWordDetector
│   ├── VoiceActivityDetector
│   ├── SpeechRecognizer
│   ├── TextToSpeech
│   └── VoiceCoordinator
│
├── nativeplatform/
│   ├── PlatformBridge
│   ├── windows/
│   │   ├── WindowsBridge
│   │   ├── WindowsMonitorService
│   │   └── WindowsWindowService
│   └── macos/
│       └── future...
│
├── update/
│   ├── ManifestClient
│   ├── ModelDownloader
│   ├── AssetDownloader
│   ├── ChecksumVerifier
│   ├── SignatureVerifier
│   └── AtomicInstaller
│
└── config/
    ├── AppConfig
    ├── CharacterConfig
    └── PersonaConfig
```

---

# 46. Backend Purpose

The backend is a **control and distribution plane**.

It should not proxy user conversations.

Responsibilities:

- authentication;
- account/device metadata;
- app release metadata;
- model versions;
- compatible quantizations;
- animation packs;
- voice-model versions;
- signed download links;
- update manifests;
- integrity metadata;
- rollout channels.

---

# 47. Backend Architecture

Recommended first version:

```text
Spring Boot Modular Monolith
          │
          ├── Auth
          ├── Users
          ├── Devices
          ├── Models
          ├── Assets
          ├── Releases
          ├── Downloads
          └── Observability
          │
          ├── PostgreSQL
          ├── optional Redis
          └── Object Storage / CDN
```

Do not introduce microservices purely for appearance.

---

# 48. Backend Download Flow

```text
Desktop
   ↓
GET /models/recommended
   ↓
Backend
   ↓
hardware compatibility decision
   ↓
model manifest
   ↓
signed URL
   ↓
Desktop downloads directly from CDN/object storage
   ↓
.partial file
   ↓
checksum verification
   ↓
signature verification
   ↓
runtime health check
   ↓
atomic install
```

The large model file should not be streamed through Spring Boot.

---

# 49. Model Manifest

Example:

```json
{
  "model": "companion-slm",
  "version": "1.3.2",
  "format": "GGUF",
  "quantization": "Q4_K_M",
  "size": 2300000000,
  "sha256": "...",
  "signature": "...",
  "minimumRamGb": 8,
  "recommendedRamGb": 16,
  "downloadUrl": "signed-url"
}
```

---

# 50. Hardware Detection

Desktop application should detect:

- OS;
- architecture;
- CPU;
- system RAM;
- GPU vendor;
- available VRAM if possible;
- available inference backend.

Then select an appropriate package.

Example:

```text
8 GB RAM / CPU only
    → smaller Q4 model

16 GB RAM / modern GPU
    → Q4/Q5 model

32 GB+ / high VRAM
    → higher quantization or larger model
```

---

# 51. Resumable Downloads

Large models require resumable downloads.

Required behavior:

```text
download
   ↓
connection interrupted at 82%
   ↓
resume with HTTP Range
   ↓
complete
   ↓
verify
```

Do not restart multi-gigabyte downloads unnecessarily.

---

# 52. Atomic Update

```text
download v4 → v4.partial
        ↓
checksum
        ↓
signature
        ↓
model runtime health test
        ↓
rename/install atomically
        ↓
mark v4 active
```

If new model fails:

```text
rollback → previous known-good version
```

---

# 53. Backend Deep-Engineering Areas

Even with a small backend, it can demonstrate:

- Spring Boot architecture;
- OAuth/OIDC;
- access/refresh tokens;
- device sessions;
- rate limiting;
- caching;
- ETags;
- signed URLs;
- resumable downloads;
- Range requests;
- optimistic concurrency;
- database migrations;
- model versioning;
- release channels;
- rollback;
- integrity verification;
- structured logs;
- tracing;
- metrics;
- ASVS security controls;
- benchmark methodology.

---

# 54. Performance Targets

Do not benchmark SLM inference as generic HTTP RPS.

Separate workloads.

## Backend control plane

Potential target:

```text
500–600 sustained metadata/API RPS
```

Measure:

- p50;
- p95;
- p99;
- error rate;
- CPU;
- memory;
- DB connections;
- cache hit ratio.

## Local SLM

Measure:

- time to first token;
- tokens/sec;
- memory;
- VRAM;
- context-size impact;
- generation latency.

## Voice

Measure:

- wake detection latency;
- VAD latency;
- ASR latency;
- TTS first-audio latency;
- end-to-end voice response latency.

## Animation

Measure:

- FPS;
- frame time;
- CPU/GPU usage;
- dropped frames.

---

# 55. End-to-End Latency Budget

Target user experience should feel immediate.

Conceptual latency chain:

```text
wake detection
+
speech boundary detection
+
ASR
+
SLM TTFT
+
TTS first audio
```

Goal:

minimize time between the user's end-of-speech and the character beginning to answer.

This should be a primary research/performance metric.

---

# 56. Security Threat Model

CompanionOS handles:

- microphone;
- local conversation;
- model files;
- character images;
- account tokens;
- update manifests;
- executable/runtime updates.

Threats include:

## Backend

- account takeover;
- IDOR;
- broken authorization;
- token theft;
- rate abuse;
- malicious signed URL use;
- CDN abuse.

## Download/update

- model supply-chain attack;
- MITM;
- corrupted model package;
- malicious asset pack;
- downgrade attack;
- rollback attack.

## Local runtime

- malicious `persona.md`;
- malformed animation pack;
- path traversal;
- unsafe deserialization;
- arbitrary native command execution;
- untrusted plugin/asset execution.

## AI

- prompt injection through persona;
- malformed structured output;
- model hallucinating capabilities;
- output trying to invoke unsupported actions.

---

# 57. Security Principles

Required:

```text
treat model output as untrusted
treat downloaded assets as untrusted until verified
verify SHA-256
verify digital signatures
validate manifest schema
validate animation schema
sandbox or constrain native actions
never execute raw LLM text
least privilege
secure token storage
safe update rollback
```

---

# 58. Privacy Principles

Default product principle:

```text
microphone audio stays local
ASR text stays local
conversation stays local
persona stays local
model inference stays local
```

Backend should not receive conversational telemetry by default.

If optional diagnostics are ever added, they must be explicit and privacy-preserving.

---

# 59. Research Layer

The project should not be only implementation.

Research should happen during development.

Potential paper:

## Working title

**Design and Evaluation of a Local-First Voice-Driven Animated Desktop Companion Using a Quantized Small Language Model**

Possible research questions:

1. What SLM size gives the best balance of natural conversation and local latency?
2. How much quality is lost under 4-bit quantization?
3. Which fine-tuning strategy gives the highest persona adherence?
4. How much latency comes from ASR vs SLM vs TTS?
5. Can a 2D skeletal animation runtime provide convincing desktop-character behavior with minimal GPU cost?
6. How accurately can persona instructions control style after fine-tuning?
7. How much does contextual history improve conversation before latency becomes unacceptable?

---

# 60. AI Experiments

## Experiment A — Base model comparison

```text
Model A
vs
Model B
vs
Model C
```

Metrics:

- conversational quality;
- persona adherence;
- multilingual quality;
- TTFT;
- tokens/sec;
- RAM;
- VRAM.

---

## Experiment B — Quantization

```text
FP16
vs
Q8
vs
Q5
vs
Q4
```

Measure:

- quality regression;
- persona regression;
- latency;
- memory.

---

## Experiment C — Fine-Tuning

```text
Base model
vs
SFT
vs
QLoRA
```

Measure:

- style adherence;
- response naturalness;
- instruction following;
- response length;
- general capability regression.

---

## Experiment D — Context Size

```text
2 recent turns
vs
4
vs
8
vs
16
```

Measure:

- conversational coherence;
- latency;
- memory;
- irrelevant-context confusion.

---

# 61. Voice Experiments

Potential comparisons:

```text
ASR runtime A
vs
ASR runtime B
```

Metrics:

- WER;
- English;
- Russian;
- mixed-language;
- latency;
- RAM;
- CPU.

Wake word metrics:

- false acceptance rate;
- false rejection rate;
- detection latency.

---

# 62. Animation Experiments

Possible comparison:

```text
sprite/cutout
vs
mesh deformation
```

Measure:

- implementation complexity;
- frame time;
- CPU/GPU use;
- visual quality;
- rigging effort.

---

# 63. Architecture Decision Records

Create `docs/adr/`.

Possible ADRs:

```text
ADR-001-local-first-ai.md
ADR-002-why-no-server-conversation.md
ADR-003-javafx-overlay.md
ADR-004-cutout-animation-v1.md
ADR-005-structured-llm-output.md
ADR-006-local-asr.md
ADR-007-model-quantization.md
ADR-008-spring-modular-monolith.md
ADR-009-cdn-direct-download.md
ADR-010-resumable-updates.md
```

Every ADR should include:

- context;
- options;
- decision;
- trade-offs;
- consequences.

---

# 64. Project Repository Structure

```text
CompanionOS/
│
├── README.md
├── CompanionOS.md
│
├── desktop/
│   └── Java desktop application
│
├── backend/
│   └── Spring Boot control plane
│
├── ai/
│   ├── datasets/
│   ├── training/
│   ├── evaluation/
│   └── quantization/
│
├── assets/
│   ├── sample-character/
│   └── animation-pack/
│
├── benchmarks/
│   ├── backend/
│   ├── inference/
│   ├── voice/
│   └── animation/
│
├── docs/
│   ├── architecture/
│   ├── adr/
│   ├── security/
│   ├── research/
│   └── diagrams/
│
└── deployment/
```

---

# 65. Development Phases

## Phase 0 — Technical Spikes

Before full implementation:

1. Transparent JavaFX window on Windows.
2. Move overlay around desktop.
3. Play a simple skeletal animation.
4. Run a local SLM from Java.
5. Capture microphone.
6. Local wake word.
7. Local ASR.
8. Local TTS.

If one of these fails, fix the architecture before building everything else.

---

# 66. Phase 1 — Desktop Character MVP

Implement:

- transparent JavaFX Stage;
- always-on-top;
- character PNG;
- movement;
- IDLE;
- WALK;
- basic state machine;
- manual rig;
- `rig.json`;
- basic animation clips.

Definition of Done:

```text
character can visibly walk across Windows desktop
without blocking the rest of the application
and maintains stable rendering
```

---

# 67. Phase 2 — Voice Loop

Implement:

```text
microphone
→ wake word
→ VAD
→ ASR
→ static response
→ TTS
```

No SLM yet if needed.

Definition of Done:

```text
user says wake word and one sentence
character hears it and answers aloud
without keyboard/mouse input
```

---

# 68. Phase 3 — Local SLM

Implement:

- local runtime;
- persona loader;
- recent conversation buffer;
- Context Builder;
- structured response;
- schema validator;
- voice response.

Definition of Done:

```text
user can edit persona.md
restart/reload character
and audibly observe the changed speech style
```

---

# 69. Phase 4 — AI + Animation Integration

SLM output:

```text
speech
emotion
gesture
```

Behaviour Engine selects:

- animation;
- emotional state;
- gesture;
- TTS settings.

Definition of Done:

```text
character speaks and reacts consistently
with the semantic content of its answer
```

---

# 70. Phase 5 — Backend

Implement:

- login;
- device registration;
- manifests;
- model registry;
- animation registry;
- signed download URLs;
- object storage;
- versioning.

Definition of Done:

```text
clean client installation can discover
and securely download all required runtime assets
```

---

# 71. Phase 6 — Update System

Implement:

- resumable downloads;
- `.partial`;
- SHA-256;
- digital signature;
- atomic install;
- rollback.

Definition of Done:

```text
forced network failure during model update
does not corrupt currently working model
```

---

# 72. Phase 7 — Model Training

Implement:

- dataset processing;
- SFT/QLoRA;
- automated evaluation;
- quantization;
- export;
- benchmark.

Definition of Done:

```text
fine-tuned model outperforms base model
on CompanionOS-specific evaluation
without unacceptable general regression
```

---

# 73. Phase 8 — Engineering Hardening

Add:

- observability;
- backend load tests;
- AI latency benchmarks;
- security tests;
- malformed manifest tests;
- corrupted package tests;
- interruption tests;
- microphone failure recovery;
- model crash recovery.

---

# 74. Definition of Done — Component Level

A component is not considered implemented merely because a dependency is present.

Example:

```text
JavaFX added
```

is not enough.

A component is considered properly implemented when:

```text
real runtime flow
+
failure case
+
test
+
measurement
+
documentation
+
trade-off explanation
```

exists.

---

# 75. Definition of Done — Full Project

CompanionOS V1 is complete when all of the following are true:

## Desktop

- character renders on Windows;
- transparent overlay works;
- character can move;
- character has at least 5 meaningful animations;
- animation does not freeze during AI inference.

## Voice

- always-on wake word;
- VAD;
- local ASR;
- local TTS;
- interruption/barge-in.

## AI

- local fine-tuned SLM;
- quantized runtime;
- persona.md conditioning;
- recent conversation context;
- structured response;
- emotion + gesture selection.

## Backend

- authentication;
- model manifest;
- asset manifest;
- secure signed downloads;
- direct CDN/object-storage delivery;
- resumable download;
- versioning;
- atomic updates.

## Security

- integrity checks;
- signature checks;
- schema validation;
- no arbitrary model-controlled execution;
- safe token handling;
- documented threat model.

## Research

- model benchmark;
- quantization benchmark;
- voice benchmark;
- end-to-end latency breakdown;
- documented trade-offs;
- research paper or technical report.

---

# 76. V1 Must-Have Scope

```text
Java desktop application
JavaFX transparent overlay
Windows support
manual character rig
2D skeletal/cutout animation
IDLE
WALK
WAVE
TALK
SLEEP
basic emotional states

local wake word
local VAD
local ASR
local fine-tuned SLM
local TTS
barge-in

persona.md
recent conversation context
structured SLM output

Spring Boot backend
auth
model/assets manifests
versioning
signed download
CDN/object storage
checksum/signature verification
resumable model download
atomic installation
rollback
```

---

# 77. V1 Should-Have

```text
basic lip sync
hardware model recommendation
multiple monitor support
animation blending
voice-selection system
model benchmark tool
backend load benchmark
OpenTelemetry
```

---

# 78. Not V1

Do not include initially:

- server-side conversations;
- server-side user learning;
- per-user LoRA;
- automatic fine-tuning from conversation;
- 8B–12B local model requirement;
- full arbitrary-image neural animation;
- 3D character;
- full autonomous desktop agent;
- kernel-level integration;
- Kubernetes;
- service mesh;
- artificial Kafka/Saga architecture;
- full automatic visual pose/rig generation.

---

# 79. Future V2 Ideas

Potential future upgrades:

## Character

- automatic pose detection;
- automatic rigging;
- mesh deformation;
- facial animation;
- better viseme lip sync;
- physics for hair/clothing;
- multi-character desktop.

## OS awareness

- foreground application detection;
- window geometry;
- sitting on window edges;
- avoiding UI;
- workspace awareness.

## AI

- better multilingual fine-tuning;
- optional local long-term memory;
- optional tool APIs with explicit user permissions;
- multimodal screen understanding;
- local image understanding.

## Voice

- speaker identification;
- emotional TTS;
- custom voice cloning with explicit consent;
- streaming ASR;
- lower-latency streaming TTS.

## Distribution

- model delta updates;
- animation marketplace;
- signed community character packs;
- multiple release channels.

---

# 80. Ultimate Product Vision

The long-term vision is:

```text
user turns on computer
       ↓
CompanionOS starts
       ↓
character appears naturally on desktop
       ↓
walks / sleeps / reacts
       ↓
user speaks without touching keyboard
       ↓
wake phrase activates character
       ↓
local AI understands and replies
       ↓
voice + expression + movement happen together
       ↓
persona is fully controlled by local persona.md
       ↓
no cloud conversation required
```

The experience should feel less like opening an AI application and more like having a persistent software character living on the desktop.

---

# 81. Final Architectural Summary

```text
                         COMPANIONOS

                            CLOUD
                              │
              ┌───────────────┴──────────────┐
              │      Spring Boot Backend     │
              │                              │
              │ Auth                         │
              │ Device metadata              │
              │ Model manifests              │
              │ Asset manifests              │
              │ Release/update metadata      │
              │ Signed download URLs         │
              └───────────────┬──────────────┘
                              │
                       Object Storage / CDN
                              │
                              ▼

════════════════════════ USER MACHINE ════════════════════════

                    Java Desktop Runtime
                              │
     ┌────────────────────────┼─────────────────────────┐
     │                        │                         │
     ▼                        ▼                         ▼
Voice Engine               AI Engine             Character Engine
     │                        │                         │
Mic                          persona.md                Rig
↓                            +                         ↓
KWS                          runtime rules          Skeleton
↓                            +                         ↓
VAD                          recent turns          AnimationMixer
↓                            +                         ↓
ASR ────────────────────────► SLM                  Renderer
                               │                      │
                               ▼                      │
                        structured output              │
                         ┌──────┴──────┐               │
                         ▼             ▼               │
                       speech       behaviour ──────────┘
                         │
                         ▼
                        TTS
                         │
                         ▼
                      Speaker
```

The core architectural boundary is:

```text
SERVER:
distribution + control

LOCAL:
intelligence + voice + animation + conversation
```

That boundary should remain stable unless there is a strong technical reason to change it.

---

# 82. Short Project Pitch

**CompanionOS** is a local-first voice-driven AI desktop companion built around a fine-tuned quantized Small Language Model, local speech recognition and TTS, and a custom 2D skeletal animation engine. Users customize the companion through a human-readable `persona.md`, while a Java desktop runtime integrates voice, AI inference, animation, and Windows overlay behavior. A Spring Boot backend is used only as a secure distribution/control plane for models, animations, assets, releases, and application updates.

