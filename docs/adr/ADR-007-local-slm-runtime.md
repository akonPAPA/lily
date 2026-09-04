# ADR-007: Local SLM runtime (Phi-4-mini via Ollama; llama.cpp fallback)

Status: Accepted (V1)

## Context

CompanionOS runs its SLM locally (ADR-001). We need (a) a model and (b) a runtime
that serves it to the Java app without blocking the JavaFX thread (§29/§30). The plan
chose llama.cpp's `llama-server` as a managed subprocess speaking an OpenAI-compatible
API. Two constraints emerged on the target machine:

1. **Model size/latency** — base Phi-4 is ~14B (~9 GB Q4); too heavy for the local-first,
   low-latency target (§11). Phi-4-mini-instruct (~3.8B, 2.32 GB Q4_K_M) fits.
2. **Binary trust** — Windows Defender blocks the unsigned prebuilt `llama-server.exe`
   from launching (access denied at CreateProcess; AppLocker/WDAC-usermode/SmartScreen
   ruled out). Changing security settings is out of scope for the tooling.

## Decision

- **Model:** Phi-4-mini-instruct, off-the-shelf GGUF **Q4_K_M**. No fine-tuning in V1;
  personality is entirely `persona.md` prompt-conditioning.
- **Runtime (V1):** **Ollama** — a code-signed, self-managing local server that exposes
  the same OpenAI-compatible API. The already-downloaded GGUF is imported without
  re-downloading via `models/Phi4Mini.Modelfile` → model `companion-phi4-mini`.
- **Structured output:** request an OpenAI `response_format: json_schema` whose schema
  is generated from the `Emotion`/`Gesture` enums, so the runtime grammar-constrains
  output to valid, in-vocabulary JSON (§16). The `ModelResponseParser` still validates
  and safe-defaults (§17), and can salvage the spoken line from malformed JSON.
- **Fallback:** the managed `llama-server` path is retained in code
  (`LocalModel.forManagedServer`) for users/platforms where the signed-runner route is
  not desired and the binary can run.

The Java client is backend-agnostic (`LocalModel.forExternalServer` vs
`forManagedServer`); switching runtimes is a config change.

## Trade-offs

- (+) Ollama is signed → no antivirus fight; it manages model load/unload and GPU
  offload (Vulkan/CUDA) itself.
- (+) Schema-constrained decoding removes the invalid-JSON failure mode at the source.
- (+) GGUF reused across runtimes; model swap is one line.
- (−) Adds an external dependency (Ollama) users must install; not a self-contained
  bundle. Packaging/bundling a signed runtime is deferred to the distribution work
  (Milestones E/F).
- (−) Verified latency (~1.1–1.6 s warm on an RTX 4060 via Vulkan) is a first data point,
  not yet the §55 end-to-end budget.

## Consequences

`AppConfig` carries both `OLLAMA_*` and `LLAMA_SERVER_*` settings. Distribution
(Milestone E) must decide how the model + a signed runtime reach end users.
