# ORION low-end device validation

This document defines a repeatable validation plan. It is not a claim that the listed devices or measurements have already been tested.

## Device tiers

| Tier | RAM | CPU/ABI | Purpose |
|---|---:|---|---|
| Minimum target | 4 GB | ARM64 | Verify install, first-run downloads, ASR/TTS/LLM sequential loading, conversation, recovery |
| Recommended | 6 GB | ARM64 | Typical daily use; sustained voice session and task/phone flows |
| High | 8 GB+ | ARM64 | Longer sessions, model lifecycle and thermal behavior |

The Samsung SM-A176B (ARM64, Android 17/API 37) is the current owner test device; its exact physical RAM capacity must be recorded from diagnostics during the next integrated test.

## Measurements to capture

Use the local diagnostic export. Do not include transcripts, conversation text, contact names, or phone numbers.

- RAM total/available, app PSS, peak PSS, minimum available RAM.
- Model install state and loaded-in-RAM state for ASR, LLM and TTS.
- ASR decode latency and audio duration; LLM first-token/total latency and throughput; TTS synthesis and first-audio latency.
- Storage free, battery/temperature, Android thermal status.
- Crashes, ANRs, recent errors, download interruption/recovery.

## Manual acceptance scenarios

1. Fresh install: automatic downloads start without manual initiation; progress advances; app remains responsive.
2. Interrupt download (network off/app process stopped), relaunch and resume/retry; verify integrity and missing-component reconciliation.
3. Sequential model load/unload: no simultaneous unnecessary residency; UI reports correct loaded state.
4. 10 consecutive short turns and 3 longer turns; no stuck listening, orphan TTS, or process crash.
5. Interrupt speech, start a new turn; stale audio must not continue.
6. Background/foreground and screen lock; app recovers state safely.
7. Low-storage condition: downloads fail safely and report actionable error without corrupting installed models.
8. Thermal/battery observation during a 10-minute voice session; note throttling or thermal warnings.
9. Reminder scheduling and notification delivery; reboot/reschedule behavior, permissions denied and later granted.
10. Contact search and call confirmation flows; never initiate a call without explicit confirmation.

## Current instrumentation

DeviceMetrics already records RAM, available RAM, low-memory flag, PSS, battery, thermal state and free storage. The diagnostics export includes model file sizes/loading states, audio routing/capture, ASR and call telemetry, task/permission state, recent errors and local crash log.

## Thresholds / response policy

These are operational warning levels, not hardware certification:
- Available RAM below 700 MB or Android low-memory flag: warn and avoid loading another large model.
- App PSS above 1.8 GB on a 4 GB device: investigate; record peak and available RAM.
- Free storage below 1.5 GB before downloading the LLM: warn; require enough space for download, temporary file and extraction.
- Thermal status moderate or higher: warn and consider reducing background work; severe status should pause optional work.
- Any ANR/crash, repeated ASR failure, stale TTS after interrupt, or model validation failure: fail scenario and attach sanitized diagnostic export.

Measurements must be taken on physical devices; CI builds and Robolectric cannot establish real-time audio performance, memory pressure, battery drain, or thermal behavior.
