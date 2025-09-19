# agents.md

> Purpose: A single-source design doc describing the autonomous *agents* and message contracts that implement the update, execution, diagnostics, UI synchronization, and policy behaviors for the Android `redlib` wrapper.

This file is intentionally prescriptive and engineer-oriented so an automation agent (or a human) like Jules can follow it step-by-step. Each agent section contains inputs, outputs, failure modes, events, acceptance criteria, and small example payloads.

---

## Table of contents

1. Overview
2. Agents (roles)

   * UpdateAgent
   * RunAgent
   * DiagnosticsAgent
   * UIAgent
   * PolicyAgent
3. Message contracts / event schemas
4. Event sequences (sample JSON flows)
5. Acceptance criteria
6. Directory & file layout
7. UX-driven hooks and notification contracts
8. Security & privacy checklist
9. Testing checklist
10. Sample pseudocode (Kotlin-coroutines style)
11. Assumptions

---

## 1) Overview

The app's runtime is split into small single-responsibility agents (not separate OS processes — these are components inside the app). Each agent emits and consumes typed events. The UI observes those events and renders state. The goal: reliability, observable state, graceful recovery, and clear diagnostics when things go wrong.

Events MUST be idempotent and timestamped. Every important step (download started, extract completed, run started, health ok, etc.) must emit an event the UI can consume.

---

## 2) Agents (roles)

### 2.1 UpdateAgent

**Goal:** Check for new release, download the release tarball, verify checksum/signature if available, extract the binary, perform atomic install and sanity check, and record metadata and backups.

**Inputs:**

* User action: `update_now`.
* Scheduled trigger from PolicyAgent (e.g., `auto_update_check`).
* Manual install (user-supplied file import).

**Outputs / Events:**

* `UpdateStarted {id, url, timestamp}`
* `UpdateProgress {id, phase, bytesDownloaded, totalBytes?, percent?}`
* `UpdateExtracted {id, tempPath, entries}`
* `UpdateSanityCheck {id, success, versionOutput?, notes?}`
* `UpdateCompleted {id, installedPath, checksum, version, timestamp}`
* `UpdateFailed {id, reason, errorLog}`
* `RollbackPerformed {id, restoredPath, timestamp}`

**Phases:** `fetch -> verify -> extract -> make_executable -> sanity -> swap -> record`.

**Failure modes & strategies:**

* Network failure: retry with exponential backoff (e.g., 1s, 2s, 4s, max 5 tries). Emit `UpdateProgress` with `phase=fetch` and `error` field.
* Corrupt archive: fail fast, keep old binary, emit `UpdateFailed` with recovery suggestion.
* Sanity check failed: do not swap; keep backup; emit `UpdateFailed` and `diagnostics` event.
* Permission/SELinux denied during exec: emit `UpdateFailed` with `SELinuxDenied` code and include `ls -Z` style hint if available.

**Security considerations:**

* Run downloads in a sandboxed thread; write to `.../redlib/tmp/<id>/` and never execute from tmp until swapped.
* Prefer checksum or signature verification. If none available, record remote headers + size and mark as unverified.
* Restrict backup retention (N latest) to prevent storage abuse.

**Retry/backoff:**

* Fetch retries with jitter. Extract errors are non-retryable (likely archive bad).

**Acceptance criteria:**

* `redlib --version` returns a parsable version string OR the release tag is recorded and install is considered successful.
* Binary file exists at `filesDir/redlib/current/redlib` and is executable.
* Checksum (if available) matches.

### 2.2 RunAgent

**Goal:** Launch the chosen `redlib` binary with a chosen env profile and arguments. Stream stdout/stderr, report exit code and runtime, support cancellation, and optionally attach a PTY for interactive apps.

**Inputs:**

* `RunRequest {id, binaryPath, args[], envProfileName, workingDir, pty: bool, runMode: foreground|background}`
* `CancelRun {id}` (user requested stop)
* `KillRun {id}` (force kill)

**Outputs / Events:**

* `RunStarted {id, pid?, timestamp}`
* `RunLine {id, stream: stdout|stderr, text, timestamp}` (emitted as streaming lines)
* `RunStatus {id, exitCode?, status: running|stopped|killed, timestamp}`
* `RunResult {id, exitCode, durationMs, stdoutSummary, stderrSummary, logsPath}`
* `RunFailed {id, reason, stderrSample}`

**Failure modes & strategies:**

* Binary not found / not executable -> emit `RunFailed` with actionable hint.
* Exec format / dynamic loader missing -> `RunFailed` and suggest cross-compile or static binary.
* Health probe failing after start: mark `running_unhealthy` and surface logs.
* Long-running process killed by OS: capture last-known exit reason (if available), persist logs.

**Cancellation strategy:**

* `Stop` = send SIGTERM (or Process.destroy()), wait `graceMs` (configurable, default 3000ms), then send SIGKILL.
* Use coroutine cancellation to stop streaming readers and emit `RunStatus`.

**PTY / Interactive mode:**

* If `pty=true`, spawn native PTY via NDK or use Termux emulation. PTY mode changes streaming to a single `RunLine` channel and supports input events `RunInput {id, text}`.

**Acceptance criteria:**

* Streaming lines flow to UI in near real-time (low latency).
* Accurate exit code and duration are recorded in `RunResult`.

### 2.3 DiagnosticsAgent

**Goal:** Collect logs, system metadata, and minimal reproducible info to help debug installs/runs. Provide a bundle that can be shared or uploaded.

**Inputs:**

* `CollectDiagnostics {context: install|run|user_request, targetId?}`
* Trigger from UpdateAgent on `UpdateFailed`.

**Outputs / Events:**

* `DiagnosticsCollected {id, bundlePath, size, scrubbed: bool, timestamp}`
* `DiagnosticsCollectionFailed {id, reason, partialBundlePath?}`

**What to collect:**

* Last N log files from `/files/redlib/logs/` (stdout/stderr).
* Update logs (download history, timestamps, headers, checksums attempted).
* Device info: android SDK, ABI, SELinux mode, available RAM, disk free, network interfaces and IPs.
* Binary metadata: file path, size, mode, `readelf -l` output (if tool available), last few lines of stdout/stderr from attempted run.

**Scrubbing rules:**

* Remove or redact any environment variable keys that match `(?i).*KEY.*|.*SECRET.*|.*TOKEN.*|.*PASS.*` unless user explicitly opts in.
* Replace values with `REDACTED` and preserve key names.

**Security:**

* Store bundles in `filesDir/redlib/diagnostics/` and only keep for a short period (configurable, default 7 days) or until user shares.

**Acceptance criteria:**

* Bundle contains at least: update log, binary path, last stderr sample, device info. If any of these cannot be read, note explicit reason in manifest.

### 2.4 UIAgent

**Goal:** Bridge agent events to UI state. Expose an observable state model (StateFlow) that Compose ViewModels can collect.

**Inputs:**

* Events from other agents (`Update*`, `Run*`, `Diagnostics*`, `Policy*`).
* User actions from UI (Start/Stop/Update/CollectDiagnostics/SetEnv/etc.).

**Outputs:**

* `UiState` objects (stitched together): `HomepageState`, `UpdateState`, `RunState`, `EnvProfiles`, `DiagnosticsState`.
* User-friendly error messages (transform raw error into actionable string + suggested next step).

**Behavior & rules:**

* Normalize event timestamps to device time and ensure UI shows last-known state even if the agent restarts.
* Keep history (recent N runs/updates) but present a summarized view.

**Failure modes:**

* If event bus disconnected, UI falls back to cached state and shows `stale` badge.

**Acceptance criteria:**

* Every important agent event is rendered in the UI within 200–500ms of emission (subject to coroutine scheduling).

### 2.5 PolicyAgent

**Goal:** Make decisions for auto-update scheduling, retention, and runtime policies (e.g., Wi-Fi only, battery/charging thresholds).

**Inputs:**

* System signals (Network type changes, battery state), user settings.
* Scheduled timers (WorkManager triggers).

**Outputs:**

* `AutoUpdateTrigger {reason, timestamp}` -> consumed by UpdateAgent.
* `RetentionPolicy` updates for UpdateAgent to prune backups.

**Rules & examples:**

* If `auto_update=WiFiOnly`, schedule update only when `NetworkState == WIFI`.
* If `battery < 20%` and `onBattery`, defer updates.

**Acceptance criteria:**

* PolicyAgent must not trigger updates when constraints are violated. WorkManager should reflect the policy via WorkInfo.

---

## 3) Message contracts / event schemas (JSON)

### UpdateProgress

```json
{
  "type": "UpdateProgress",
  "id": "uuid",
  "phase": "fetch|verify|extract|sanity|swap",
  "bytesDownloaded": 12345,
  "totalBytes": 98765,
  "percent": 12.5,
  "timestamp": "2025-09-19T...Z"
}
```

### RunLine

```json
{
  "type": "RunLine",
  "id": "run-uuid",
  "stream": "stdout",
  "text": "Starting server on 0.0.0.0:8080",
  "timestamp": "..."
}
```

### RunResult

```json
{
  "type": "RunResult",
  "id": "run-uuid",
  "exitCode": 0,
  "durationMs": 1234,
  "stdoutSummary": "first line...",
  "stderrSummary": "",
  "logsPath": "files/redlib/logs/run-uuid.log"
}
```

### DiagnosticsCollected

```json
{
  "type": "DiagnosticsCollected",
  "id": "diag-uuid",
  "bundlePath": "files/redlib/diagnostics/diag-uuid.zip",
  "scrubbed": true
}
```

---

## 4) Event sequences (sample flows)

### 4.1 Successful update

1. UI -> `update_now` action.
2. UpdateAgent emits `UpdateStarted`.
3. `UpdateProgress` phased events (fetch -> extract -> sanity).
4. `UpdateSanityCheck` success with `version`.
5. `UpdateCompleted` with installedPath and recorded checksum.
6. UI shows new version and records history.

### 4.2 Failed update and rollback

1. UpdateAgent `UpdateStarted`.
2. Fetch OK; Extract OK; Sanity fails.
3. `UpdateFailed` emitted with `reason: sanity_failed` and `stderrSample`.
4. DiagnosticsAgent auto-triggered to collect a bundle and emits `DiagnosticsCollected`.
5. UpdateAgent performs no swap and emits `RollbackPerformed` (restored old binary) if it had partially swapped.
6. UI shows actionable message: "Update failed: binary failed sanity check. Share diagnostics?"

### 4.3 Run with streaming output and cancellation

1. UI -> `RunRequest` with `envProfile=dev`.
2. RunAgent emits `RunStarted {pid}`.
3. Streaming `RunLine` events arrive.
4. User taps `Stop` -> `CancelRun` sent.
5. RunAgent sends SIGTERM, waits, then sends SIGKILL if necessary.
6. RunAgent emits `RunResult` with exitCode and logsPath.

---

## 5) Acceptance criteria (per agent)

* **UpdateAgent**: binary exists, executable, sanity check passes OR rollback performed and user informed.
* **RunAgent**: streaming lines delivered and final `RunResult` contains exit code and a path to persisted logs.
* **DiagnosticsAgent**: bundle containing at least logs + device metadata produced on demand.
* **UIAgent**: shows correct process status (stopped/running/unhealthy) and the correct effective port/host.
* **PolicyAgent**: respects network/battery constraints; WorkManager Jobs reflect policy.

---

## 6) Directory & file layout (recommended)

```
/files/redlib/
  current/            # installed binary + runtime artifacts
    redlib            # executable
    config/           # optional config files
  backups/
    2025-09-19-.../   # previous installs (keep N latest)
  logs/
    run-<uuid>.log
    update-<uuid>.log
  tmp/
    <update-id>/      # downloads & extracted artifacts
  diagnostics/
    diag-<uuid>.zip
```

Permissions: All under app `filesDir` with app UID ownership. Keep least privilege.

---

## 7) UX-driven hooks & notification contract

* **On long-running run**: post a foreground service notification with:

  * Title: `redlib running`
  * Body: `Listening on 0.0.0.0:8080 — tap to open`.
  * Actions: `Stop` (sends cancel), `Open` (bring app to foreground).

* **On failed install**: push an in-app dialog with "Install failed — collect diagnostics" button. If user taps, generate `DiagnosticsCollected` bundle and open share sheet.

* **Health check**: UI displays a small colored pill `Healthy|Unhealthy|Unknown` and allows re-check.

---

## 8) Security & privacy checklist

* Do NOT include env values that match `(?i).*KEY.*|.*SECRET.*|.*TOKEN.*|.*PASS.*` in diagnostics unless user explicitly opts in.
* Store env profiles in DataStore; encrypt sensitive profiles using Android `EncryptedFile`/`EncryptedSharedPreferences` if user toggles "store secrets".
* Limit diagnostic retention; delete after share or after TTL.
* Avoid logging full stdout/stderr unbounded: rotate logs and cap per-file size (e.g., 2MB) and total retention (e.g., 50MB).

---

## 9) Testing checklist / integration tests

* Unit tests:

  * Parse `--version` output variations.
  * `effectivePort()` logic for env precedence.
* Integration tests (on CI or dedicated Android test device via adb):

  * Fake tarball with a simple Hello binary; run download->extract->install->run flow.
  * Simulate corrupt tarball and assert rollback.
  * Run cancellation test: send Stop and ensure SIGTERM+SIGKILL path works.
* E2E tests:

  * Using a mock server hosting release artifacts, test UpdateAgent retries and progress events.
* Manual tests:

  * SELinux deny scenario (some devices) — capture error and diagnostic.

---

## 10) Sample pseudocode (Kotlin-coroutines style)

### UpdateAgent (sketch)

```kotlin
suspend fun runUpdate(url: String): UpdateResult = coroutineScope {
  val id = UUID.randomUUID().toString()
  eventBus.emit(UpdateStarted(id, url, now()))
  try {
    val tmp = downloadToTmp(url) { bytes, total -> eventBus.emit(UpdateProgress(...)) }
    verify(tmp) // optional
    val extracted = extractTarGz(tmp)
    val bin = findBinary(extracted, "redlib")
    makeExecutable(bin)
    val sanity = runSanityCheck(bin)
    if (!sanity.ok) throw SanityFailed(sanity)
    atomicSwap(bin, installPath)
    recordMetadata(installPath, checksum)
    eventBus.emit(UpdateCompleted(...))
    return UpdateResult.Success(...)
  } catch (e: Throwable) {
    eventBus.emit(UpdateFailed(id, e.message ?: "unknown", captureLog(e)))
    rollbackIfNeeded()
    return UpdateResult.Failure(e)
  }
}
```

### RunAgent (sketch)

```kotlin
fun runBinary(request: RunRequest): Flow<RunEvent> = flow {
  val pb = ProcessBuilder(listOf(request.binaryPath) + request.args)
  val env = System.getenv().toMutableMap().apply { putAll(loadProfile(request.envProfileName)) ; ensureDefaults() }
  pb.environment().putAll(env)
  pb.directory(File(request.workingDir))
  val proc = pb.start()
  eventBus.emit(RunStarted(request.id, proc.pid(), now()))
  // launch readers
  val stdoutJob = launch { proc.inputStream.bufferedReader().forEachLine { eventBus.emit(RunLine(request.id, "stdout", it, now())) } }
  val stderrJob = launch { proc.errorStream.bufferedReader().forEachLine { eventBus.emit(RunLine(request.id, "stderr", it, now())) } }
  val exitCode = proc.waitFor()
  stdoutJob.cancelAndJoin(); stderrJob.cancelAndJoin()
  persistLogs(request.id)
  eventBus.emit(RunResult(request.id, exitCode, durationMs, ...))
}
```

---

## 11) Assumptions & notes (document what Jules often misses)

* Assume `redlib` binary in tarball is named `redlib` or path ends with `/redlib`. If archive layout differs, UpdateAgent must accept a `binaryNamePattern` override.
* Assume no signature available on releases — document verification as "best effort" and expose a toggle for signature verification if you later add keys.
* Assume app runs on Android arm64 devices; dynamic linking may fail — always include friendly remediation: "Cross-compile for `aarch64-linux-android` or use a static musl build."
* Explicitly record every error with human-friendly text and at least one suggested next step.

---

### Deliverables expected from a follow-up run by an implementation agent

* `agents.md` (this file). ✅
* JSON schema files for each event type (compact).
* A tiny reference implementation: `UpdateAgent` and `RunAgent` Kotlin files (coroutines + simple event bus) with unit tests.
* Example diagnostic bundle sample.

---

If anything in here should be expanded into separate docs (event schemas, acceptance test matrix, or a developer playbook for cross-compiling), say which one and I’ll generate it next.
