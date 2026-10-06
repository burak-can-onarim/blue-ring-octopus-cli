# Usage guide

Everything you can do with Blue Ring Octopus CLI, plus fixes for the problems people hit most often.
For installation, see the [README](../README.md#quick-start).

- [Before you start](#before-you-start)
- [The full-screen UI](#the-full-screen-ui)
- [Modes](#modes)
- [Choosing a model](#choosing-a-model)
- [One-shot commands](#one-shot-commands)
- [Configuration reference](#configuration-reference)
- [Troubleshooting](#troubleshooting)

## Before you start

1. Install and start [Ollama](https://ollama.com/download). Check that it answers:

   ```bash
   curl http://localhost:11434/api/tags
   ```

2. Pull at least one model. The default is `qwen2.5-coder`:

   ```bash
   ollama pull qwen2.5-coder
   ```

3. Run the application with **JDK 25**. `java -version` should report 25.

## The full-screen UI

Start the application without arguments:

```bash
java --enable-native-access=ALL-UNNAMED -jar blue-ring-octopus-cli.jar
```

```
┌ Blue Ring Octopus CLI ─────────────────────────────────┬ Model ──────────────┐
│ banner                                                  │ model for this mode │
│ Prompt area  (output + multi-line input)                │ ● selected          │
│ Path field                                              │ + installed         │
├ Mode ┬ Status / progress ───────────────────────────────┤ - not installed     │
│ key hints                                                                      │
└────────────────────────────────────────────────────────────────────────────────┘
```

On Windows the UI opens in its own window (the title is *Blue Ring Octopus CLI*); closing it with the **X** button
asks for confirmation. On Linux and macOS it draws inside the current terminal, so use a terminal with true-colour
and UTF-8 support.

### Keyboard shortcuts

| Key | Action |
|---|---|
| <kbd>Enter</kbd> | Run the current mode |
| <kbd>Shift</kbd>+<kbd>Enter</kbd>, <kbd>Alt</kbd>+<kbd>Enter</kbd> or a trailing `\` | Insert a new line in the prompt |
| <kbd>Tab</kbd> / <kbd>Shift</kbd>+<kbd>Tab</kbd> | Next / previous mode |
| <kbd>↑</kbd> / <kbd>↓</kbd> | Move between lines of a multi-line prompt, then step through input history |
| <kbd>Ctrl</kbd>+<kbd>P</kbd> | Switch focus between the Prompt field and the Path field |
| <kbd>Ctrl</kbd>+<kbd>L</kbd> | Open (or close) the model panel for the current mode |
| <kbd>Ctrl</kbd>+<kbd>V</kbd>, <kbd>Shift</kbd>+<kbd>Insert</kbd> | Paste. In the Path field, surrounding quotes from Windows "Copy as path" are removed |
| <kbd>Ctrl</kbd>+<kbd>C</kbd> | Copy the output of the last run to the clipboard |
| <kbd>PgUp</kbd> / <kbd>PgDn</kbd> | Scroll the output while typing |
| Mouse wheel | Over the output: scroll it. Over the prompt: scroll a long prompt without moving the caret |
| Left click | Focus the Prompt or Path field and place the caret where you clicked |
| <kbd>Esc</kbd> | While a task runs: ask to cancel it. Otherwise: ask to exit |

Typing `exit` (or `cikis`) in the prompt and pressing <kbd>Enter</kbd> also quits.

**Mouse.** Only three things respond to the mouse, on purpose: the wheel over the output, the wheel over a long prompt, and a left click on the Prompt or Path field. Everything else (modes, the model list, dialogs) stays on the keyboard. Mouse support currently exists in the Windows window only; in a Linux/macOS terminal the mouse keeps its normal behaviour, so you can still select and copy text with it.

## Modes

Switch modes with <kbd>Tab</kbd>. The hint line above the input shows what the current mode expects.

| Mode | Prompt | Path | What it does |
|---|---|---|---|
| **Kod Analizi** (code analysis) | not used | file or folder; empty means the working directory | Reviews every `.java` file found: bugs and security, performance, Clean Code. The report is written in Turkish. |
| **Kod Generate** (code generation) | what to write, up to 2000 characters | optional target `.java` file | Writes one Java type. Empty path means `generated/<TypeName>.java`. Existing files are never overwritten. |
| **Döküman Hazırlama** (documentation) | – | – | Planned. Selecting it prints a "not ready yet" message. |
| **Birim Test Yazdırma** (unit tests) | – | – | Planned. Selecting it prints a "not ready yet" message. |

Limits and behaviour worth knowing:

- Files larger than **64 KB** are skipped (and listed) so the local model is not overloaded.
- These folders are never scanned: `.git`, `.idea`, `.mvn`, `.vscode`, `.gradle`, `target`, `build`, `node_modules`, `logs`.
- Code generation is **Java only**. A request that names another language (Python, C#, ...) without mentioning Java is
  rejected before the model is called.
- Relative paths are resolved against the working directory shown in the title of the Path field.

## Choosing a model

Each mode remembers its own model, so you can use a small fast model for reviews and a larger one for generation.

1. Press <kbd>Ctrl</kbd>+<kbd>L</kbd>.
2. Move with <kbd>↑</kbd> <kbd>↓</kbd>, choose with <kbd>Enter</kbd>, leave with <kbd>Esc</kbd>.
3. A `+` means Ollama has the model, `-` means it does not. Choosing a missing model prints the exact
   `ollama pull <name>` command to run.

The list contains the suggested models (`qwen2.5-coder`, `qwen2.5-coder:14b`, `qwen3-coder:30b`,
`deepseek-coder-v2:16b`, `llama3.1`, `codellama`, `gpt-oss:20b`) plus anything else you have installed.
Your choice is stored in `~/.octopus-cli/models.properties`.

## One-shot commands

When you pass arguments the UI is skipped: the command runs, prints to standard output and exits. This is what you want
in scripts, CI jobs and containers.

```bash
# analyze: review a file or a folder (default: current directory)
java --enable-native-access=ALL-UNNAMED -jar blue-ring-octopus-cli.jar analyze --pathInput ./src

# generate: write a class, optionally to a chosen file
java --enable-native-access=ALL-UNNAMED -jar blue-ring-octopus-cli.jar generate \
  --prompt "A Spring REST controller for products" --out src/main/java/demo/ProductController.java

# discover everything else
java --enable-native-access=ALL-UNNAMED -jar blue-ring-octopus-cli.jar help
java --enable-native-access=ALL-UNNAMED -jar blue-ring-octopus-cli.jar help generate
```

| Command | Options | Description |
|---|---|---|
| `analyze` | `--pathInput <file or folder>` (default `.`) | Review Java files. |
| `generate` | `--prompt <text>` (required), `--out <file.java>` (optional) | Generate and save a Java class. |
| `ui`, `dashboard` | – | Open the full-screen UI. |
| `mode`, `mod` | – | Interactive mode selector. |
| `help`, `history`, `version`, `script` | – | Built-in Spring Shell commands. |

The one-shot `analyze` and `generate` commands always use the default model (`AI_MODEL_NAME`, or `qwen2.5-coder`)
unless you have saved a different model for that mode in the UI.

## Configuration reference

| Setting | Where | Default |
|---|---|---|
| `AI_MODEL_NAME` | environment variable | `qwen2.5-coder` |
| `LANGCHAIN4J_OLLAMA_CHAT_MODEL_BASE_URL` | environment variable | `http://localhost:11434` |
| `langchain4j.ollama.chat-model.temperature` | `application.yaml` or `LANGCHAIN4J_OLLAMA_CHAT_MODEL_TEMPERATURE` | `0.2` |
| `langchain4j.ollama.chat-model.timeout` | `application.yaml` or `LANGCHAIN4J_OLLAMA_CHAT_MODEL_TIMEOUT` | `5m` |
| `logging.file.name` | `LOGGING_FILE_NAME` | `logs/octopus.log` |
| Per-mode model | UI model panel | stored in `~/.octopus-cli/models.properties` |

Any Spring Boot property can be overridden with an environment variable (dots and dashes become underscores, upper
case) or with `--property=value` on the command line.

## Troubleshooting

**The model panel says "Ollama'ya ulaşılamadı" (Ollama is unreachable).**
Start Ollama (`ollama serve`) and check `curl http://localhost:11434/api/tags`. If Ollama runs elsewhere (another
machine, or the Docker host), set `LANGCHAIN4J_OLLAMA_CHAT_MODEL_BASE_URL`.

**"Model kurulu değil" (model not installed).**
Run the `ollama pull <name>` command that the application prints.

**The analysis stops with a timeout.**
Large models on CPU can take minutes per file. Raise `langchain4j.ollama.chat-model.timeout` (default `5m`) or pick a
smaller model.

**`UnsupportedClassVersionError` or "class file version 69.0".**
The JAR needs Java 25. Run `java -version` and install a JDK 25 build.

**A `WARNING: A restricted method ... has been called` message appears.**
Add `--enable-native-access=ALL-UNNAMED` to the `java` command, as in every example here.

**Docker: the container cannot reach Ollama.**
On Linux add `--add-host=host.docker.internal:host-gateway` to `docker run`. If Ollama only listens on
`127.0.0.1`, start it with `OLLAMA_HOST=0.0.0.0` so the container can connect.

**Docker: generated files cannot be written.**
The image runs as an unprivileged user. Run it as yourself: `docker run --user "$(id -u):$(id -g)" ...`.

**A `blue-ring-octopus-cli.log` file appears in my project folder.**
It is the Spring Shell command history that one-shot commands write to the working directory. It is safe to delete and
is covered by `*.log` in this repository's `.gitignore`. The Docker image disables it.

Still stuck? [Open an issue](https://github.com/burak-can-onarim/blue-ring-octopus-cli/issues/new/choose) with your
OS, `java -version`, the model you used and the relevant lines of `logs/octopus.log`.
