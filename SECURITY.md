# Security Policy

## Supported versions

Blue Ring Octopus CLI is in the `0.x` series. Security fixes are made on the latest released minor version and on
`master`; older versions are not patched.

| Version | Supported |
|---|---|
| Latest `0.x` release | ✅ |
| `master` | ✅ |
| Older releases | ❌ |

## Reporting a vulnerability

**Please do not report security problems in public issues, discussions or pull requests.**

Use GitHub's private reporting instead:

1. Open the [Security tab](https://github.com/burak-can-onarim/blue-ring-octopus-cli/security) of the repository.
2. Choose **Report a vulnerability**.
3. Describe the problem, how to reproduce it, the affected version and the impact you see.

Only the maintainers can see the report. This is a small, volunteer-run project, so these are goals rather than
guarantees:

- an acknowledgement within about **a week**;
- an assessment and a rough timeline soon after;
- a fix released as soon as it is ready, and credit in the release notes if you want it.

Please give us a reasonable chance to fix the problem before you disclose it publicly.

## What is in scope

The application runs on your own machine and talks to an Ollama server you control, so the interesting risks are
local ones. Reports about the following are welcome:

- reading or writing files **outside** the paths the user asked for (path traversal, symlink handling);
- overwriting or deleting existing files;
- sending source code anywhere other than the configured Ollama address;
- command or code injection through prompts, file contents or configuration;
- vulnerable dependencies that are actually reachable from this application;
- secrets or personal data written to logs or settings files.

## What is out of scope

- The behaviour or output quality of a language model (for example, an unhelpful review). Model output is untrusted
  by design; always read generated code before you use it.
- Vulnerabilities in Ollama itself; please report those to the [Ollama project](https://github.com/ollama/ollama).
- Problems that require an attacker to already control your account, your machine or your Ollama server.

## A note on safe use

- The default Ollama address is `http://localhost:11434`. If you point the application at a remote server, your source
  code travels to that server.
- Generated code is never executed by the application, but you should review it before compiling or running it.
- Code generation creates new files only and refuses to overwrite an existing one.
