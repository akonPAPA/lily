# ADR-001: Local-first AI (no server-side conversation)

Status: Accepted

## Context

CompanionOS is a voice companion that must feel private and work offline. Sending
microphone audio or conversation text to a server would create latency, privacy
exposure, and a hard dependency on connectivity (spec §1, §2, §58).

## Options

1. **Cloud LLM wrapper** — send ASR text to a hosted model. Simplest to build; best raw
   quality; worst privacy/offline/latency; ongoing per-user cost.
2. **Local SLM, server only for distribution** — run wake word, VAD, ASR, SLM, TTS on the
   user's machine; backend only serves models/assets/updates.
3. **Hybrid** — local for most turns, cloud fallback for hard ones. Adds complexity and
   reintroduces the privacy/offline problems for those turns.

## Decision

Option 2. Conversation, inference, and voice are strictly local. The backend is a
control/distribution plane and never receives conversational telemetry by default.

## Trade-offs

- (+) Privacy, offline use, no per-conversation cost, low round-trip latency.
- (+) Clear security boundary: model output is untrusted and never leaves the device.
- (−) Bounded by what a 2–4B-class quantized model can do on consumer hardware.
- (−) Model distribution/update becomes a first-class engineering problem (Milestones E/F).

## Consequences

The SLM runs via a local `llama-server` (ADR-007). The backend design (Milestone E) is
scoped to auth + manifests + signed downloads only.
