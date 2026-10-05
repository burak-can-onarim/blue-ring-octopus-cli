# Architecture

This document explains how Blue Ring Octopus CLI is put together: the layers, the main flows, where state is kept
and which design rules the code follows. All diagrams use [Mermaid](https://mermaid.js.org/), which GitHub renders
natively.

- [System context](#system-context)
- [Layers and components](#layers-and-components)
- [The mode layer](#the-mode-layer)
- [Flow: code analysis](#flow-code-analysis)
- [Flow: code generation](#flow-code-generation)
- [Data and persistence](#data-and-persistence)
- [Threading and cancellation](#threading-and-cancellation)
- [Design rules](#design-rules)
- [Extending the application](#extending-the-application)

## System context

The application is a single process. The only thing it talks to over the network is the Ollama HTTP API, which is
expected to run on the same machine (or on a host you configure).

```mermaid
flowchart LR
    dev([Developer])
    subgraph host["Your machine"]
        app["Blue Ring Octopus CLI<br/>(JVM, Spring Boot)"]
        ollama[("Ollama<br/>:11434")]
        fs[("Project files<br/>.java")]
        cfg[("~/.octopus-cli/<br/>models.properties")]
    end
    dev -- "terminal UI / one-shot command" --> app
    app -- "reads" --> fs
    app -- "writes generated/*.java" --> fs
    app -- "HTTP /api/chat, /api/tags" --> ollama
    app -- "remembers model per mode" --> cfg
```

Nothing is sent to a third-party service. If you point `LANGCHAIN4J_OLLAMA_CHAT_MODEL_BASE_URL` at a remote Ollama
instance, your code goes to that instance, so only do that with infrastructure you trust.

## Layers and components

```mermaid
flowchart TB
    subgraph entry["Entry points"]
        direction LR
        starter["TuiStartupListener<br/>no arguments: open the UI"]
        shell["Spring Shell commands<br/>analyze · generate · mode · ui"]
    end

    subgraph ui["Terminal UI (Lanterna)"]
        window["MainWindow<br/>PromptArea · InputHistory · ClipboardSupport"]
    end

    subgraph modes["Mode layer"]
        dispatcher["ModeDispatcher"]
        handlers["AnalysisModeHandler · GenerateModeHandler<br/>Document / UnitTest (planned)"]
    end

    subgraph ai["AI layer"]
        registry["AiServiceRegistry"]
        service["ICodeAnalyzerService<br/>(prompt templates)"]
        models["ModelSettings · InstalledModels · ModelCatalog"]
    end

    scanner["SourceCodeScanner"]
    ollama[("Ollama")]

    starter --> window
    window --> dispatcher --> handlers
    shell -->|"analyze, generate"| handlers
    shell -->|"ui"| window
    window --> models
    handlers --> scanner
    handlers --> registry --> service
    registry --> models
    registry -->|"chat (OllamaChatModel)"| ollama
    models -->|"GET /api/tags"| ollama
```

The UI picks the handler for the current mode through `ModeDispatcher`; the one-shot commands call
`AnalysisModeHandler` and `GenerateModeHandler` directly. Both paths end up in the same handler code.

| Package | Responsibility |
|---|---|
| `cli` | Spring Shell commands for one-shot use (`analyze`, `generate`). |
| `command` | Spring Shell `mode` selector. |
| `config` | Spring wiring: executor, startup runner, shell prompt, `AppInfo` (version shown in the banner). |
| `context` | `AppMode` (the four modes and their hints) and `AppContext` (the current mode). |
| `mode` | One handler per mode, the dispatcher, the `ModeRequest` value object and the `IModeConsole` abstraction. |
| `model` | Everything about *which* model is used: client registry, installed-model discovery, per-mode settings, suggestions. |
| `service` | `ICodeAnalyzerService` (the LLM prompts) and `SourceCodeScanner`. |
| `tui` | The Lanterna user interface. |
| `util` | `PathUtils`: quote stripping and relative-path resolution. |

## The mode layer

Modes are the unit of behaviour. `AppMode` lists them; each one must have exactly one `IModeHandler`. `ModeDispatcher`
verifies that at start-up and fails fast if a mode has no handler or two handlers.

```mermaid
classDiagram
    direction LR
    class AppMode {
        <<enum>>
        KOD_ANALIZI
        KOD_GENERATE
        DOKUMAN_HAZIRLAMA
        BIRIM_TEST
        +next() AppMode
        +previous() AppMode
    }
    class IModeHandler {
        <<interface>>
        +mode() AppMode
        +handle(ModeRequest, IModeConsole)
    }
    class IModeConsole {
        <<interface>>
        +step(String)
        +println(String)
        +progress(int, int)
        +isCancelled() boolean
    }
    class ModeRequest {
        <<record>>
        +prompt String
        +path String
    }
    class ModeDispatcher {
        +dispatch(AppMode, ModeRequest, IModeConsole)
    }
    class PlannedModeHandler {
        <<abstract>>
    }
    IModeHandler <|.. AnalysisModeHandler
    IModeHandler <|.. GenerateModeHandler
    IModeHandler <|.. PlannedModeHandler
    PlannedModeHandler <|-- DocumentModeHandler
    PlannedModeHandler <|-- UnitTestModeHandler
    ModeDispatcher o-- IModeHandler
    IModeHandler ..> ModeRequest
    IModeHandler ..> IModeConsole
    IModeHandler ..> AppMode
```

`IModeConsole` is the seam between behaviour and presentation. The terminal UI implements it with a cancellable
`Job` that updates the status line, progress bar and output panel; the one-shot commands use `IModeConsole.stdout()`.
Handlers therefore contain no UI code and behave identically in both front ends.

## Flow: code analysis

```mermaid
sequenceDiagram
    autonumber
    actor U as Developer
    participant W as MainWindow / CodeAgentCLI
    participant A as AnalysisModeHandler
    participant S as SourceCodeScanner
    participant R as AiServiceRegistry
    participant M as Ollama

    U->>W: Enter (path)
    W->>A: handle(request, console)
    A->>A: resolve path, check it exists
    A->>S: scanJavaFiles(path)
    S-->>A: .java files (ignored folders removed)
    A->>R: forMode(KOD_ANALIZI)
    R-->>A: ICodeAnalyzerService for the selected model
    loop every file
        A->>A: cancelled? skip files over 64 KB or blank
        A->>M: analyze(code) via LangChain4j
        M-->>A: review text
        A-->>W: console.println(review) / progress(i, total)
    end
    A-->>W: "Analiz tamamlandı"
```

Guards: files over **64 KB** are skipped and reported, blank files are ignored, a missing path is reported instead of
failing, and the loop checks for cancellation before every file.

## Flow: code generation

```mermaid
flowchart TD
    start([prompt + optional --out]) --> validate{"Valid?<br/>not blank · ≤ 2000 chars<br/>Java request · .java path"}
    validate -- no --> err1[/"Explain what is wrong"/]
    validate -- yes --> llm["Model writes the class<br/>(system prompt: raw Java only)"]
    llm --> cancelled{Cancelled?}
    cancelled -- yes --> stop([Nothing written])
    cancelled -- no --> strip["Strip Markdown fences"]
    strip --> detect{"Contains a<br/>class / interface / enum / record?"}
    detect -- no --> err2[/"Warn and show raw output<br/>(not saved)"/]
    detect -- yes --> target["Target = --out, or<br/>generated/&lt;TypeName&gt;.java"]
    target --> write["Write with CREATE_NEW"]
    write -- file exists --> err3[/"Refuse to overwrite"/]
    write -- ok --> done([Saved])
```

The language check is a deliberate heuristic: a request that names another language (and not Java) is rejected
*before* the model is called, so no time is spent on output the tool would not accept.

## Data and persistence

**There is no database, and that is intentional.** The application has no multi-user state, no relations to
query and nothing that must survive a crash beyond one tiny preference. Adding a database server (or even an embedded
one) would add a dependency, a failure mode and a migration story without buying anything.

| What | Where | Format | Written when |
|---|---|---|---|
| Model chosen for each mode | `~/.octopus-cli/models.properties` | Java properties, keyed by `AppMode` name | A model is picked in the model panel |
| Application log | `logs/octopus.log` (override with `LOGGING_FILE_NAME`) | Text | Always (console output is switched off so it does not corrupt the UI) |
| Generated code | `generated/<TypeName>.java` or the `--out` path | Java source | Only in code generation mode, never over an existing file |

Model lookup order for a mode: the saved choice, otherwise `AI_MODEL_NAME`, otherwise `qwen2.5-coder`.

If this ever changes (conversation history, analysis reports that should be searchable, team sharing), start with
an embedded store such as SQLite and an ADR describing why a file is no longer enough.

## Threading and cancellation

- The UI runs on Lanterna's GUI thread. Long work never runs there: each task is submitted to `aiTaskExecutor`, a
  virtual-thread-per-task executor.
- A running task is a `Job`. Cancelling sets a flag, interrupts the thread and makes the job ignore any further
  output, so the UI is free again immediately even if the model keeps computing.
- Handlers poll `console.isCancelled()` between files and before writing to disk, so a cancelled generation never
  leaves a half-written file.
- `InstalledModels.refresh()` (the `/api/tags` call) also runs on the executor; when it finishes it hands the result
  back to the UI thread to redraw the model panel. The spinner has its own daemon thread.

## Design rules

1. **Local-first.** No telemetry, no hosted service. Limits (64 KB per file, 2000-character prompts) exist to keep
   requests small and fast on a local model.
2. **Never destroy user data.** Files are created with `CREATE_NEW`; cancellation happens before writes.
3. **Handlers don't know about the UI.** They talk to `IModeConsole` only.
4. **Fail fast at start-up.** A mode without a handler is a programming error caught by `ModeDispatcher`.
5. **Small, testable units.** Pure helpers (`PathUtils`, `AppInfo.normalize`, `InputHistory`, `SourceCodeScanner`)
   are plain classes that are unit-tested without a Spring context.

## Extending the application

To add a new mode (for example the planned documentation writer):

1. Add a constant to `AppMode` with its display name and the prompt/path hints.
2. Add a prompt method to `ICodeAnalyzerService` (or a new service interface).
3. Implement `IModeHandler` (or replace the `PlannedModeHandler` subclass for that mode). Use `IModeConsole` for all
   output and honour `isCancelled()`.
4. Add unit tests; `ModeDispatcher` already guarantees the wiring is complete.

To support another model backend, create the chat model in `AiServiceRegistry.create(...)`; the rest of the code only
sees `ICodeAnalyzerService`.
