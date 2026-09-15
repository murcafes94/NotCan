# MiniCPM experiment for NotCan

Branch: `feature/minicpm-local-experiment`

## Goal

Evaluate MiniCPM5 and MiniCPM-V as optional local engines for TuNot without replacing the current stable local path until they pass compilation, device tests and NotCan Bench.

The experiment must remain reversible: the APK must continue to work with Gemma 4 and `Local básico` even if MiniCPM is not installed or the experimental runtime fails.

## Phase 1 implemented in this branch

- Add provider ids for `MiniCPM5 local` and `MiniCPM-V local` to the Provider Gateway.
- Add capability metadata so future routing can select a provider by text/reasoning/tool/vision/video capability instead of hard-coding model names.
- Add a pure Kotlin MiniCPM catalog with the upstream LiteRT-LM bundles currently documented by OpenBMB:
  - MiniCPM5-1B CPU, ~0.79 GB.
  - MiniCPM5-1B GPU optimized, ~0.79 GB.
  - MiniCPM5-2B int4, ~1.55 GB, CPU/GPU and the preferred phone candidate upstream.
- Add an Android `DownloadManager` based installer/remover for those bundles. Models remain outside the APK and require an explicit download.
- Track MiniCPM-V 4.6 as the vision candidate with separate GGUF model + F16 vision projector metadata.
- Add unit tests for model metadata and provider capabilities.

## Runtime compatibility gate

Stable NotCan 1.0.1 currently uses `com.google.ai.edge.litertlm:litertlm-android:0.11.0` for Gemma 4.

OpenBMB's current MiniCPM5 Android deployment guide validates MiniCPM5 with LiteRT-LM `0.17.0`. This first phase intentionally does **not** change the stable runtime dependency. Download/install lifecycle is separated from inference so we can establish a green baseline before upgrading LiteRT-LM.

The next phase is allowed only after this branch compiles and tests successfully:

1. upgrade LiteRT-LM to 0.17.0 on this branch only;
2. repair any Gemma 4 API incompatibilities;
3. add a `MiniCpmLiteRtTuNotEngine` using the same cancellation, streaming, timeout and metrics conventions as Gemma;
4. keep thinking disabled by default for normal answers and expose a bounded reasoning mode only when useful;
5. compare MiniCPM5-1B / 2B against Gemma with NotCan Bench before changing any default routing.

## Vision path

MiniCPM-V 4.6 is not wired into inference yet. The upstream Android demo uses llama.cpp with a GGUF language model and a separate visual projector. NotCan already carries a pinned llama.cpp submodule, so the preferred design is to extend our own Android/JNI layer rather than embedding the upstream demo application.

Planned flow:

`photo / scanned page / diagram -> MiniCPM-V local -> structured text/context -> TuNot Harness`

Normal PDFs with a usable text layer should continue through the lighter existing document extraction/RAG path; MiniCPM-V should only be loaded when visual understanding adds value.

## Licensing / provenance

- `OpenBMB/MiniCPM-V` repository: Apache-2.0.
- MiniCPM model weights and converted bundles must be reviewed at the exact model-card/version used before a production release.
- No MiniCPM source code is copied into NotCan in phase 1.
- `OpenBMB/MiniCPM-V-Apps` is used only as an implementation reference until its redistribution terms are independently confirmed for the exact code we would reuse.

## Promotion criteria

MiniCPM must not become a production default until all of the following are true:

- release unit tests pass;
- release APK builds and signs successfully;
- Gemma regression tests remain green after the LiteRT upgrade;
- real Android device test covers CPU and, where supported, GPU;
- cancellation releases the conversation/runtime correctly;
- model deletion cleans storage and does not break TuNot;
- NotCan Bench shows acceptable Spanish, academic, document-grounded and theology performance;
- RAM, first-token latency, tokens/s and battery/thermal behavior are recorded.
