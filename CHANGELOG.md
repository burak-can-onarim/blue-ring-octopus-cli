# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). While the major version is `0`, minor versions may
contain breaking changes.

## [Unreleased]

> This section also covers the work that was developed as `0.1.1` and `0.1.2` (see the version history in `pom.xml`)
> but was never tagged or published.

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

[Unreleased]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/compare/v0.0.1...v0.1.0
[0.0.1]: https://github.com/burak-can-onarim/blue-ring-octopus-cli/releases/tag/v0.0.1
