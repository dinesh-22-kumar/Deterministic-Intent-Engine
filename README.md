# ⚡ DIVM: Deterministic Intent Virtual Machine
> High-performance, zero-code autonomous execution engine built purely in native Java with zero third-party frameworks, libraries, or external brokers.

---

## 🏛️ Architectural Overview

DIVM provides an in-memory, deterministic execution environment where computation pipelines are dynamically compiled from plain-text intent DSLs, scheduled as Directed Acyclic Graphs (DAGs), and executed with strict concurrency and sandboxing controls.

```text
       [Declarative Intent DSL]
                  │
                  ▼
          [IntentCompiler]
                  │
                  ▼
     [ExecutionGraph (Kahn's DAG)]
                  │
      ┌───────────┴───────────┐
      ▼                       ▼
 [Branch A: Worker]      [Branch B: Worker]
      └───────────┬───────────┘
                  ▼ (Barrier Synchronization)
          [Merge Aggregator]
                  │
       ┌──────────┴──────────┐
       ▼                     ▼
 [StateJournal (WAL)]  [StateSnapshot]

---

## 🚀 Core Primitives

* **Graph Scheduler (`ExecutionGraph.java`)**: Implements Kahn's Algorithm for topological sorting and cycle prevention, ensuring fully deterministic computation ordering.
* **Lock-Free Concurrency (`DeterministicDispatcher.java`)**: Supports non-linear fork-join diamonds using `CompletableFuture` multi-parent synchronization barriers.
* **Hot-Compiling DSL (`IntentCompiler.java`)**: Parses and links raw string declarations (e.g., `A(OP) -> B(OP)`) into executable graphs at runtime without restarting.
* **Fault-Tolerant Sandboxing (`KernelNode.java`)**: Every node runs inside an isolated watchdog thread with hard timeout budgets (default 1500–3000ms), automatic retry circuits, and seamless fallback switching.
* **Persistent Logging (`StateJournal.java`)**: Sequential Write-Ahead Logging (WAL) via Java NIO `FileChannel` append-only operations directly to `dvm_state.wal`.
* **Zero-Framework HTTP Engine (`EngineServer.java`)**: Native `com.sun.net.httpserver` web layer leveraging modern lightweight virtual thread dispatchers.

---

## 🛠️ REST API Specification

### 1. Dynamic Pipeline Compilation

Registers and verifies a new execution DAG on the fly.

```bash
curl.exe -X POST http://localhost:8080/compile \
  -H "X-Pipeline-Id: security_flow" \
  -d "INGEST(TRIM_LOWERCASE) -> SERIALIZE(TO_BYTES) -> SECURE(BASE64_ENCODE)"

  2. Pipeline Execution
Dispatches a raw payload through the target pipeline.

Bash
curl.exe -X POST http://localhost:8080/execute \
  -H "X-Pipeline-Id: security_flow" \
  -d "   ZERO_DEPENDENCY_MICRO_KERNEL   "
3. State Audit & WAL Replay
Queries append-only disk journal entries for auditability and recovery.

Bash
curl.exe http://localhost:8080/journal
4. Health & Telemetry
Returns JVM memory allocation and active pipeline state.

Bash
curl.exe http://localhost:8080/health
