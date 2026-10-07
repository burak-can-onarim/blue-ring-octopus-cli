# Prompts

What the application says to the model, why it is worded that way, and what was measured. Read this before you
change a prompt.

- [Where the prompts are](#where-the-prompts-are)
- [How a call is made](#how-a-call-is-made)
- [The review](#the-review)
- [The context window](#the-context-window)
- [What was measured](#what-was-measured)
- [What did not work](#what-did-not-work)
- [Limits and next steps](#limits-and-next-steps)
- [Changing a prompt](#changing-a-prompt)

## Where the prompts are

Plain text files in `src/main/resources/prompts/`, one `<name>.system.txt` and one `<name>.user.txt` per task. The
`{{name}}` placeholders are filled in by `PromptLibrary`.

| Prompt | Used by | Notes |
|---|---|---|
| `analyze` | Code Analysis | English only; the review is always made in English |
| `translate` | Code Analysis, other languages | Translates the free text of a review; has a glossary for the five other languages |
| `generate` | Code Generation | One Java source file that compiles on its own |
| `repair` | Code Generation, after a failed compile check | The same file with the compiler's errors fixed |
| `tests` | planned Unit Tests mode | JUnit 5 test class |
| `document` | planned Documentation mode | Markdown (Word / PDF later), headings in six languages |
| `inventory` | planned Documentation mode | Markdown table of the public API (Excel later) |

`PromptLibraryTest` checks that every placeholder a prompt uses has a value, that a missing value is an error, and that
the prompts stay short enough for a small context window.

## How a call is made

`CodeAssistant` builds one system message and one user message and sends them to the model chosen for the mode. It does
not use LangChain4j's annotation templates: rendering is strict (an unknown placeholder fails fast), values such as
source code are inserted as they are and never scanned for `{{...}}`, and the whole thing can be tested with a fake
model.

## The review

A 7B coding model reasons well in English and badly when it has to analyse code and write the report in Turkish in one
go (it answered "no issues" or copied its own notes). So:

1. **Analysis in English.** The code is sent with line numbers (`12| code`), so the model can point at exact lines and
   the reader can find them. The model first writes a scratch pad between `<notes>` tags (one line per suspicion, with
   what the code really does there and a verdict), then the answer: `OVERVIEW`, `FINDINGS` (numbered, severity, line,
   consequence, fix) and `SUMMARY`. The application removes the notes, and also copes with a notes block that was never
   closed. It strips the Markdown that small models add although they are told not to (the output box shows plain text).
2. **Localized frame.** For another language the application replaces the titles, the severity words, "line",
   "Consequence", "Fix" and the "no issues" sentence by the words of the language files. This is text substitution, so
   the frame is always right.
3. **Translation.** A second call translates only the free text between those words, with a glossary of technical terms
   (SQL injection, resource leak, catch block, ...) per language.
4. **Fallback.** If the translation loses a title or a finding, the localized English review is shown instead.

English costs one call, every other language two.

## Compile and repair

The application runs on a JDK, so it can compile what the model wrote before it saves it (`CompileCheck`, the JDK's
compiler in process, no class path). What happens:

1. Extra `public` top-level types lose their `public`. The file is named after the first type and Java allows one public
   type per file, but small models mark every type they write as public (3 of 3 JPA samples).
2. The file is compiled. An error that only says that a library is missing (`package org.springframework... does not
   exist`, and the "cannot find symbol" errors of the names that import brought in, also `@Override` on a type from such a
   library) does not count: the application does not have Spring or Jakarta on its class path, so the code can only be
   checked for everything else. The packages are listed as not checked.
3. If errors of the code's own remain, the model is called once with `repair.system.txt` and `repair.user.txt`: the file,
   and the compiler's messages with line number and the text of that line. It must answer with the complete corrected
   file and change as little as possible. The code is not numbered here, because the answer must be raw source.
4. The repaired file is used only if it has **fewer** errors and the same main type. A repair that renames the type or adds
   errors is worse than no repair. The file is saved in any case; the result of the check is printed under it.

The repair is skipped when the file plus the same file again as the answer would not fit the context window.

## The context window

Ollama cuts a prompt that does not fit its context window, and without a word. With its default settings a probe with a
65,000 character prompt was read as only 2,050 tokens. The application therefore sets `num_ctx`
(`octopus.model.num-ctx`, default 8192, which still fits a 7B model entirely on an 8 GB graphics card) and skips a file
whose estimated size does not fit, with a message that names the setting. `ContextBudget` keeps 3,000 tokens free for
the instructions, the notes and the answer, and its estimate is deliberately cautious.

## What was measured

Everything below was measured with `qwen2.5-coder` (7B, Q4) on an RTX 4060, temperature 0.2, several seeds per prompt.
The samples are small Java files with known problems plus one clean file that must not get findings.

**Review** (3 files with 12 known problems in total plus the clean control file; 3 seeds for the old prompt, 4 for the final one):

| | Known problems found | Length | Markdown in the output | Clean file |
|---|---|---|---|---|
| Old prompt (0.3.0) | 81 % | ~450 words | yes, everywhere | 4 invented problems (in the run that was read in full) |
| Final prompt | 69 % | ~150 words | removed | 0 or 1 invented problem |

The final prompt finds somewhat fewer of the seeded problems than the old, long one, but it is a third of the length,
points at lines, and invents far less. It is the best balance found with this model; a larger model will do better.
Without the notes step it found 56 %. Section titles are present in 15 of 16 runs.

**Code generation** (5 requests, compiled with `javac`; 2 seeds for the old prompt, 3 for the final one): 8 of 10
compiled with the old prompt, 15 of 15 with the final one (standalone file, imports, no unknown types). The old prompt
produced no Javadoc.

**Compile and repair** (14 requests, 3 seeds each = 42 files, harder than the five above: a JSON parser, an event bus, a
rate limiter, a state machine, a JPA repository, a job runner, ...; every file compiled as the application does it):

| | Compiles |
|---|---|
| First try, as written by the model | 32 to 33 of 42 (76 to 79 %) |
| After one repair | 34 of 42 (81 %) |
| After demoting extra `public` types and one repair | 38 of 42 (90 %) |

The first two rows and the third are separate runs of the same requests and seeds; Ollama is not fully deterministic, so
a difference of one or two files between identical runs is noise. The repairs that were tried (9 files): 5 fixed all
errors, 1 some, 3 none. Repair fixes missing imports, a wrong method name or a missing cast. It does not fix what needs a
different design: the JSON parser never compiled (a `void` used as a value, a checked exception the code does not
declare), and in a generics error the model often changes the line without removing the cause. That is why a repair is
accepted only when it has fewer errors. The 5 simple requests above compiled 15 of 15 without any of this.

**Unit tests** (3 classes, compiled and run with JUnit; 2 seeds for the first version, 3 for the final one): 79 % of the
generated tests passed with the first version, 86 % with the final one. The failures are tests that expect validation
the code does not have.

## What did not work

- **Notes step for code generation.** It made the model plan layered designs with types that do not exist: 6 of 10
  compiled instead of 9 of 10.
- **Analysis and report in the target language in one call.** The model copied its notes into the answer, skipped the
  overview or fell back to English.
- **Translating the whole review.** The model translated the labels wrongly ("Düzelme" for "Fix") or left the findings
  in English. Localizing the frame first and translating only the free text fixed it.
- **A 20 line limit for the notes.** Worse than 12 (recall 52 % against 69 %). With no limit the model sometimes
  looped, listing ordinary code as "NOT REAL" until it ran out of tokens.
- **Tying the answer to the notes** ("one finding per REAL line") made the model skip the overview.

## Limits and next steps

- A thread-safe cache is still written without synchronization by this model, and test expectations for behaviour the
  code does not show are sometimes wrong. The compile-and-repair step catches compile errors but not these.
- Files that do not fit the window are skipped; reviewing them method by method would be better.
- The model lists in `ModelCatalog` are recommendations based on what the models are good at, not measurements. Only
  the 7B model was run here; a 14B or 20B model should be tried before trusting the order.

## Changing a prompt

1. Edit the file; `PromptLibraryTest` tells you if a placeholder is missing.
2. Measure on more than one sample and more than one seed (three at least), and include a clean file. A single run is
   noise: one prompt looked better than another on one seed and worse on the next.
3. Check the whole output, not only the findings: a change once made the overview disappear in every run.
4. Check at least one language other than English.
