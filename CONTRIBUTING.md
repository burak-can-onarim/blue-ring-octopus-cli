# Contributing to Blue Ring Octopus CLI

Thank you for your interest! Bug reports, ideas, documentation fixes and code are all welcome. This guide keeps
contributions smooth for everyone.

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to help

- **Report a bug** with the [bug report form](https://github.com/burak-can-onarim/blue-ring-octopus-cli/issues/new?template=bug_report.yml).
- **Suggest a feature** with the [feature request form](https://github.com/burak-can-onarim/blue-ring-octopus-cli/issues/new?template=feature_request.yml).
  For anything large, please open an issue *before* writing code so we can agree on the direction.
- **Improve the docs.** Typos, unclear steps and missing examples are all fair game.
- **Pick up an issue** labelled [`good first issue`](https://github.com/burak-can-onarim/blue-ring-octopus-cli/labels/good%20first%20issue)
  or [`help wanted`](https://github.com/burak-can-onarim/blue-ring-octopus-cli/labels/help%20wanted).
- **Report a security problem privately**; see [SECURITY.md](SECURITY.md). Please do not open a public issue for it.

## Development setup

You need **JDK 25** and, to try the application end to end, [Ollama](https://ollama.com) with a model such as
`qwen2.5-coder`. Maven is provided by the wrapper, so nothing else has to be installed.

```bash
git clone https://github.com/burak-can-onarim/blue-ring-octopus-cli.git
cd blue-ring-octopus-cli

./mvnw verify                                   # compile, test, package (mvnw.cmd on Windows)
java --enable-native-access=ALL-UNNAMED -jar target/blue-ring-octopus-cli.jar
```

If you contribute from a fork, create a branch from `master`.

The project layout and the main flows are described in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Read it before
changing the mode layer or the UI.

## Making a change

1. **Fork** the repository and create a topic branch, for example `fix/path-with-spaces` or `feat/docs-mode`.
2. **Keep the change focused.** One concern per pull request is easier to review and to revert.
3. **Write or update tests.** Logic that does not need a UI or a model should have a unit test. The existing tests in
   `src/test/java` show the style (JUnit 5, no Spring context unless it is really needed).
4. **Run `./mvnw verify`** and make sure it passes.
5. **Update the documentation** (`README`, `docs/`) and add a line under `## [Unreleased]` in
   [CHANGELOG.md](CHANGELOG.md) when the change is visible to users.
6. **Open a pull request** and fill in the template. CI must be green before a review.

### Code style

- Follow the style of the surrounding code and the [`.editorconfig`](.editorconfig) (UTF-8, LF, 4-space indent).
- Handlers must talk to the user only through `IModeConsole`; do not import UI classes into the `mode` package.
- Never overwrite or delete user files. Generated files are created with `CREATE_NEW`.
- Prefer small, pure, testable methods. Keep comments for the *why*, not the *what*.
- User-facing text in the application is currently Turkish; keep new messages consistent with the existing ones.
  Code, identifiers, commit messages and documentation are in English (Turkish is also fine for issues and discussion).

### Commit messages

This project uses [Conventional Commits](https://www.conventionalcommits.org/): `type: short summary`.

| Type | Use for |
|---|---|
| `feat` | A new feature |
| `fix` | A bug fix |
| `docs` | Documentation only |
| `test` | Adding or fixing tests |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `ci` / `build` | Workflows, Maven, Docker |
| `chore` | Maintenance such as dependency bumps |

Mark breaking changes with `!` (for example `feat!: ...`) and explain them in the body.

## Pull request checklist

- [ ] The change is focused and the PR description explains *why*.
- [ ] `./mvnw verify` passes locally.
- [ ] Tests were added or updated where it makes sense.
- [ ] Documentation and `CHANGELOG.md` are updated.
- [ ] No secrets, local paths or generated files are included.

## How releases work

Maintainers publish releases by pushing a `vX.Y.Z` tag (for example `v0.2.0`; a suffix such as `-rc.1` marks a
pre-release). The release workflow runs the tests, builds the JAR with that version, creates a GitHub Release with
generated notes, a SHA-256 checksum and a build-provenance attestation, and publishes the container image to `ghcr.io`.
The tag is the source of truth for the version; the version in `pom.xml` is only a development placeholder. Version
numbers follow [Semantic Versioning](https://semver.org/).

The release notes are generated from merged pull requests and grouped by label. A workflow labels each pull request
from its Conventional Commit title (`feat:` becomes *enhancement*, `fix:` becomes *bug*, `docs:` becomes
*documentation*, and so on), so a clear PR title is all that is needed.

You can rehearse a release without publishing anything: run the **Release** workflow manually from the Actions tab.

## Questions

Not sure whether something is a bug, or how to approach a change? Open an issue and ask. A short question now is
cheaper than a large pull request later.
