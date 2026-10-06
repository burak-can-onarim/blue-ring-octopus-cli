# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). While the major version is `0`, minor versions may
contain breaking changes.

## [Unreleased]

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

[Unreleased]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.1.3...HEAD
[0.1.3]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.1.0...v0.1.2
[0.1.0]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/releases/tag/v0.0.1
