# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). While the major version is `0`, minor versions may
contain breaking changes.

## [Unreleased]

### Added

- **Compile check and one repair for generated code.** The generated file is compiled in process (the JDK's compiler, no
  class path) before it is saved. If it has errors of its own, the model gets the file and the compiler's messages once
  and writes it again; the second version is used only if it has fewer errors and the same main type. The result is
  printed under the file (`Compile check passed.`, or a warning that lists the remaining errors). Errors that only say
  that a library is missing (Spring, Jakarta, ...) do not count; those libraries are listed as not checked. Where no
  compiler exists (the Docker image has a JRE) the check is skipped with a note. With the local 7B model, 38 of 42 files
  of 14 harder requests compiled, 33 before. See [docs/PROMPTS.md](docs/PROMPTS.md).
- The prompts `repair.system.txt` and `repair.user.txt`.
- **Files that do not fit the model's window are reviewed in parts instead of being skipped.** `CodeSplitter` cuts the
  file by method (a small scanner that skips comments, strings and text blocks, so it also works on code that does not
  compile). Every part holds the outline of the whole file and writes out only its own members, with the line numbers of
  the file, and the prompt (`analyze.part.user.txt`) asks the model to review only those lines. A method larger than the
  window is cut into overlapping pieces. Each part is announced in the output and has its own overview, findings and
  summary. In a trial on a class with 12 known problems the 7B model found about as many as for the whole file
  (within noise), but `gemma4:26b` found 35 of 36 in 4 parts against 29 of 36 for the whole file, and `gemma4:e4b`
  31 of 36 against 22. More parts also produce more, partly generic, findings. See
  [docs/PROMPTS.md](docs/PROMPTS.md).

### Changed

- A helper type that the model writes next to the main type loses its `public`, because a file can hold only one public
  type and the file is named after the first.
- The file size limit of the review goes from 64 KB to 512 KB, because big files are no longer skipped for not fitting.
  A window too small to hold even the outline and a few lines still skips the file, with the hint to raise
  `octopus.model.num-ctx`.

## [0.4.0] - 2026-10-08

### Added

- **Prompts for the four modes** (`src/main/resources/prompts/`), tuned and measured against the local model, see
  [docs/PROMPTS.md](docs/PROMPTS.md). The prompts for the planned Unit Tests and Documentation modes (Word/PDF text and
  an Excel-ready API table) are ready, the modes themselves are not.
- `octopus.model.num-ctx` (default 8192) and `octopus.model.max-tokens` (default 4096), also as the environment
  variables `OCTOPUS_MODEL_NUM_CTX` and `OCTOPUS_MODEL_MAX_TOKENS`.

### Changed

- **Code analysis explains and points at lines.** A review has an overview of what the code does, numbered findings
  with severity, line, consequence and fix, and a verdict, as plain text. The code is sent with line numbers. It is
  shorter than before (about a third) and invents far fewer problems, on a clean file as well.
- **Reviews in other languages** are made in English first; the application then puts the frame of the review (titles,
  severity, line, Consequence, Fix) into the selected language and a second call translates only the free text, with a
  glossary of technical terms. If the translation breaks the frame, the localized English review is shown.
- **Code generation** must produce one file that stands alone (imports, no unknown types, helper types nested) and
  documents the public API; comments are written in the selected language.
- The model panel lists suggestions per mode, best first (`gpt-oss:20b` for reviews and documentation,
  `qwen3-coder:30b` for generation and tests); `qwen2.5-coder` is in every list.
- The model calls go through `CodeAssistant` instead of LangChain4j annotation templates.

### Fixed

- The exit popup (and the cancel popup and the language picker) could not be used with the mouse. The buttons and the
  language rows can be clicked now.
- Ollama's default context window cut long files silently, so a big class was reviewed from a fragment. The window is
  asked for explicitly now and a file that does not fit is skipped with a message that names the setting.

## [0.3.0] - 2026-10-07

### Added

- **Select and copy text from the Output box with the mouse.** Drag to select, double click for a word, triple click
  for a line (wrapped rows are joined again). `Ctrl+C` copies the selection, or the last output when nothing is
  selected.
- **Mouse access to the model panel and the modes.** A left click on the Model panel focuses it and moves the cursor to
  the clicked model; a double click chooses it. The `‹` `›` arrows of the Mode box switch to the previous / next mode.
- A new pixel-art banner: the blue-ringed octopus next to the name in a pixel font.

### Changed

- The **Dialog** box is now called **Output** (Çıktı, Ausgabe, Sortie, Output, Salida). Every new message scrolls it to
  the bottom, also after scrolling up. `PgUp` / `PgDn` scroll it while the model panel is focused.
- The Path box title is just "Path"; it used to show the working directory, which never followed the typed path.
- The banner tagline reads "Local-first AI code analysis & generation" (it was "Local AI Code Assistant").
- Turkish texts: "Kod Generate" is now "Kod Üretimi" and "Döküman Hazırlama" is "Doküman Hazırlama".
- The mode constants have English names (`CODE_ANALYSIS`, `CODE_GENERATION`, `DOCUMENTATION`, `UNIT_TESTS`). A
  `models.properties` written by an older version is still read, and rewritten with the new names on the next save.

## [0.2.0] - 2026-10-07

### Added

- **Language selection.** The interface and the model's answers are available in English (default), Türkçe,
  Deutsch, Français, Italiano and Español. Press `Ctrl+G` to choose; the choice applies at once and is saved to
  `~/.octopus-cli/settings.properties`. The `OCTOPUS_LANG` environment variable overrides it for a run, which also
  works in Docker and for the one-shot commands. `Ctrl+G` is listed in the shortcut bar.

### Changed

- **The default language is now English** (it used to be Turkish). Switch back with `Ctrl+G` or `OCTOPUS_LANG=tr`.
- The mode names, hints, messages and dialogs come from per-language files (`i18n/messages_*.properties`); the model
  is told to answer in the selected language, and generated code gets its comments in that language.
- The help texts of the one-shot commands and the log messages are English.
- The shortcut bar no longer shows the **Giriş** and **Gezinme** group labels. The two rows start with the keys, so
  the whole width is available for shortcuts.

## [0.1.8] - 2026-10-07

### Changed

- The shortcut bar also aligns the descriptions: within a column the keys are padded to the same width, so keys,
  descriptions and the " · " separators line up across both rows.
- The legend of the model panel is stacked in three rows (green `●` selected, blue `+` installed, grey `-` missing).

## [0.1.7] - 2026-10-07

### Changed

- The model panel colours the models: the selected one green, installed ones blue, missing ones grey. The legend
  below the list uses the same colours and is styled like the shortcut bar; the "Ctrl+L" hint was removed from the
  panel because the shortcut bar already lists it. The highlighted row shows the cursor while the panel has focus.
- The shortcut bar rows are reordered: **Giriş** is Enter, Shift+Enter, Ctrl+C, Ctrl+V, ↑↓; **Gezinme** is Tab,
  PgUp/PgDn, Ctrl+P, Ctrl+L, Esc.

## [0.1.6] - 2026-10-07

### Changed

- The key hints at the bottom of the window are redesigned: two rows (**Giriş** and **Gezinme**) with the keys
  highlighted, fewer items and " · " separators that line up in columns across both rows. On a narrow window the last
  columns are dropped instead of being cut in the middle.

## [0.1.5] - 2026-10-06

> `0.1.4` was only a development version and was never tagged; this release contains everything since `0.1.3`.

### Added

- The output area is now a **conversation**: each request appears under a "Sen" heading (with mode and model) and the
  answer under "Octopus", colour-coded. The history of the session is kept in memory (up to 5000 lines) and cleared
  when the application quits.
- The view **follows the newest message** automatically; scrolling up pauses it until you reach the bottom again.
- Mouse support in the Windows window: the wheel scrolls the output and a long prompt, and a left click focuses the
  Prompt or Path field (and places the caret). Nothing else responds to the mouse.

### Changed

- The conversation and the prompt input are now separate boxes: **Diyalog** (formerly part of "Prompt Alanı") and **Prompt**.
  The Diyalog starts empty.
- The banner subtitle reads "Local AI Code Assistant".
- The prompt hint of the **Kod Generate** mode is reworded.
- The project version no longer carries a `-SNAPSHOT` suffix (the banner shows the version as it is), and the version in
  `pom.xml` is bumped with every fix or change to the product.
- New layout: the banner spans the full window width and the model panel is only as tall as the prompt and path
  fields, which leaves the banner more room.
- <kbd>Ctrl</kbd>+<kbd>C</kbd> now copies the last output instead of asking to cancel or exit; the old
  <kbd>Ctrl</kbd>+<kbd>O</kbd> shortcut is removed. <kbd>Esc</kbd> cancels the running task or asks to exit.

### Fixed

- Mouse: the whole Prompt and Path boxes (borders and hint line included) react to a left click, and touchpads and
  smooth-scrolling mice now scroll the Diyalog (fractional wheel events were ignored before).

## [0.1.3] - 2026-10-06

### Added

- Application icon: the window and taskbar now show the octopus icon, a multi-size `.ico` ships in
  `src/main/resources/icons`, and `scripts/create-shortcut.bat` creates a Desktop shortcut with that icon that starts
  `start-agent.bat`.

### Changed

- Logo and README banner use the application icon (`docs/assets/logo.png`); the social preview uses the new octopus
  artwork.

## [0.1.2] - 2026-10-06

> This release also contains the work that was developed as `0.1.1` (see the version history in `pom.xml`)
> but was never tagged or published, and everything since `0.1.0`.

### Added

- Per-mode **model selection** in the UI (<kbd>Ctrl</kbd>+<kbd>L</kbd>) with live detection of the models installed in
  Ollama, a suggested-model catalogue and persistence in `~/.octopus-cli/models.properties`.
- **Multi-line prompt** with input history (`PromptArea`, `InputHistory`), clipboard paste and *copy last output*.
- Application version in the start-up banner (`-SNAPSHOT` is hidden).
- Unit tests for the core classes (mode dispatching, requests, path handling, source scanning, model settings,
  installed-model discovery, input history).
- Project documentation: new `README` (English and Turkish), architecture and usage guides with diagrams,
  `CONTRIBUTING`, `CODE_OF_CONDUCT`, `SECURITY` and this changelog.
- Visual identity: logo, README banner and a social-preview image (`docs/assets`), plus real screenshots of the UI.
- GitHub automation: CI (Linux and Windows), CodeQL analysis, tag-driven release workflow that publishes the JAR with a
  SHA-256 checksum, Dependabot, issue forms, a pull-request template, `CODEOWNERS` and release-note categories.
- Container image published to the GitHub Container Registry on every release.
- `LICENSE` (MIT), `.editorconfig` and `.dockerignore`.

### Changed

- The project was renamed from *Code Analyzer* to **Blue Ring Octopus CLI** (package
  `com.bcoworks.blueringoctopuscli`, artifact `blue-ring-octopus-cli`).
- Request handling moved to `AiServiceRegistry`, which builds one LangChain4j service per Ollama model.
- `pom.xml` now carries complete project metadata (URL, licence, developer, SCM, issue tracker, CI).
- `Dockerfile` rewritten: removed an unused `ubuntu` stage, added a BuildKit cache for Maven, an unprivileged user,
  OCI image labels and container-friendly defaults (Ollama on `host.docker.internal`, log and history files outside
  the mounted project).

### Fixed

- The banner no longer shows `-SNAPSHOT` in the version, and the `ollama pull` hint stays readable.
- `mvnw` is now executable in git, so the Maven wrapper runs on Linux (CI and Docker builds).

## [0.1.0] - 2026-10-04

### Added

- Full-screen terminal UI built with Lanterna: banner, prompt and path fields, status line, key hints and a theme.
- Working modes: **code analysis** and **code generation**. *Documentation* and *unit-test* modes are present as
  placeholders.
- `ModeDispatcher` and `IModeConsole`, which let the same handlers serve the UI and the command line.

### Changed

- `start-agent.bat` starts the full-screen UI.

## [0.0.1] - 2026-10-04

### Added

- First public pre-release: a local AI agent client for analysing and generating Java code with Ollama, driven by
  Spring Shell commands (`analyze`, `generate`). The earlier REST endpoints had been removed in favour of the command
  line.
- `start-agent.bat` launcher for Windows with a model picker.
- Windows archive with a bundled JRE.

[Unreleased]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.1.8...v0.2.0
[0.1.8]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.1.7...v0.1.8
[0.1.7]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.1.6...v0.1.7
[0.1.6]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.1.5...v0.1.6
[0.1.5]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.1.3...v0.1.5
[0.1.3]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.1.0...v0.1.2
[0.1.0]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/releases/tag/v0.0.1
