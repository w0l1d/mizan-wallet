<!--
SYNC IMPACT REPORT
==================
Version change: [unfilled template] -> 1.0.0
Bump rationale: Initial ratification. The previous file was the unmodified core
scaffold with every placeholder token intact, so no prior governance existed to amend.

Modified principles: none (no prior named principles existed)

Added sections:
  - Core Principles I-V (Layered Architecture; Typed Errors; Illegal States
    Unrepresentable; Test-First for Domain Logic; Green Gates Before Merge)
  - Technology & Dependency Constraints
  - Delivery Workflow
  - Governance

Removed sections: none

Sources used to derive principles (no content was invented):
  - docs/guidelines/Architecture.md, Error-Handling.md, Data-Modeling.md,
    Screen-Architecture.md, Unit-Testing.md
  - CLAUDE.md (build commands, module structure, convention plugins, tech stack)
  - LICENSE (GNU GPL-3.0, inherited from the Ivy Wallet fork origin)
  - Recorded branching/merge-tag and CI-budget workflow for this repository

Deferred items:
  - TODO(SCREENSHOT_TESTING_GUIDE): docs/guidelines/Screenshot-Testing.md is an
    empty file (0 bytes). Principle V references Paparazzi verification, but the
    written guidance behind it does not exist yet and must be authored.
-->

# Mizan Wallet Constitution

## Core Principles

### I. Layered Architecture (NON-NEGOTIABLE)

Dependencies MUST flow in exactly one direction:
`feature -> shared:ui:core -> shared:domain -> shared:data:core -> shared:data:model -> shared:base`.
A module MUST NOT depend on a layer above it, and `shared/base` MUST NOT depend on any
other project module.

Data MUST be mapped through the chain _raw model -> domain model -> ViewState model_. A
Room entity, DTO, or other raw model MUST NOT reach a Composable.

Composables MUST be dumb: they display an already-formatted ViewState and emit events.
Business logic, formatting, and IO MUST NOT appear in a Composable. ViewModels translate
between the UI and the domain and own no business rules of their own.

Repositories MUST be main-safe — every repository function moves its work to a background
dispatcher and never blocks the UI thread.

Feature modules MUST expose their public API through a `public/` module when other
features consume them; implementations stay `internal`. Cross-feature access to
implementation details is forbidden.

**Rationale:** the layering is the only thing keeping a fork of this size navigable. A
single upward dependency makes a module untestable in isolation and is disproportionately
expensive to unwind later.

### II. Typed Errors Over Exceptions

Operations that can fail MUST return Arrow's `Either<Error, Data>` rather than throwing.
Error types MUST be modelled as `sealed interface` hierarchies specific to the operation,
never as bare `String` or `Throwable` where the caller needs to branch on the cause.

`throw` is permitted ONLY where a crash is the intended outcome — unrecoverable
environment failures such as exhausted disk space. Every such throw MUST carry a comment
stating why crashing is correct.

Swallowing an error — an empty `catch`, a silent `getOrNull()` that discards a failure the
user needs to know about, or a fallback that hides a real fault — is forbidden.

**Rationale:** failures in a finance app are routine, not exceptional. Encoding them in
the type system makes the compiler enforce that every caller handles them.

### III. Illegal States Unrepresentable

Data models MUST eliminate impossible states by construction. Prefer ADTs — `sealed
interface` plus `data class` — over flag combinations. A `data class` carrying
`loading: Boolean`, `error: String?`, and `content: T?` simultaneously MUST be refactored
into a sealed hierarchy.

Values with domain constraints MUST use constrained types rather than raw primitives where
such a type already exists in `shared/data/model`.

A model that admits a state the code must defensively check for is a modelling defect, and
MUST be fixed at the model rather than guarded at each call site.

**Rationale:** a model that permits fewer wrong states needs less defensive code, and the
bugs it prevents are the ones that corrupt user financial data.

### IV. Test-First for Domain Logic

Every change to domain logic, a use case, a mapper, or a calculation MUST be accompanied
by unit tests written against the behaviour, not the implementation.

Tests MUST follow Given/When/Then structure and use `io.kotest.matchers` assertions.
Parameterised cases use `@RunWith(TestParameterInjector::class)`. Mock at boundaries with
MockK; do not mock types you own and can construct directly.

Bug fixes MUST add a test that fails before the fix and passes after it.

Property tests MUST pass an explicit `runTest(timeout = ...)` via `PropertyTestTimeout`;
the 60-second default is unreliable on loaded CI runners.

**Rationale:** the balance, transfer, and zakat calculations are the product. A regression
in them is silent and is discovered by users as wrong numbers, not as a crash.

### V. Green Gates Before Merge

No branch is delivered until `./gradlew detekt`, `./gradlew testDebugUnitTest`, and
`./gradlew assembleDemo` have been run and have passed. Completion MUST be claimed only
from observed command output — never inferred, never assumed.

`./gradlew verifyPaparazziDebug` MUST be run when a module with screenshot tests is
touched, and its result MUST be reported honestly: a `NO-SOURCE` or `UP-TO-DATE` task
proves nothing and MUST NOT be cited as verification.

Any build failure, test failure, or user-reported defect MUST be recorded with its root
cause and fix before the work is considered done.

**Rationale:** CI on this repository is budget-constrained and cannot be used as the first
line of feedback. Local verification is the gate, so it has to be real.

TODO(SCREENSHOT_TESTING_GUIDE): `docs/guidelines/Screenshot-Testing.md` is empty; the
written standard behind the Paparazzi requirement must be authored.

## Technology & Dependency Constraints

Dependency versions MUST be declared in `gradle/libs.versions.toml` and referenced as
`libs.<alias>` or `libs.bundles.<alias>`. Hardcoding a version in a module build file is
forbidden.

Modules MUST be configured through the `buildSrc` convention plugins (`ivy.feature`,
`ivy.compose`, `ivy.room`, `ivy.hilt`, `ivy.paparazzi`, `ivy.widget`,
`ivy.integration.testing`) rather than by duplicating Gradle configuration.

The stack is fixed: Jetpack Compose with Material3, Hilt, Room, Ktor Client, Coroutines
and Flow, Arrow, Kotlinx Serialization. Introducing a library that overlaps an existing
one — a second HTTP client, DI framework, or result type — requires an explicit recorded
decision under Governance.

`temp/` holds legacy code being migrated. New code MUST NOT be added to it, and code
touched there SHOULD be moved toward its proper module.

This project is a fork of Ivy Wallet and is distributed under the **GNU GPL-3.0**. All
contributions are licensed under GPL-3.0. Dependencies MUST carry a GPL-3.0-compatible
licence; source-available-but-not-open licences MUST NOT be shipped in the app.

## Delivery Workflow

Feature work MUST live on a branch whose prefix matches its conventional-commit type —
`feat/`, `fix/`, `perf/`, `refactor/` — cut from `main` when independent of unmerged
`develop` work, otherwise from `develop`.

Each delivery MUST be recorded with an annotated tag `merged/<branch-name>.<n>` at the
branch tip, stating what was delivered and what was verified, and merged with
`git merge --no-ff` so `git log --first-parent` reads as one line per delivery. A record
tag MUST NOT begin with `v`; that namespace triggers release builds.

Production delivery goes through a single `develop -> main` pull request, not per-feature
PRs to `main`, so that one combined APK is produced for testing.

CI workflows MUST stay within the repository's billing budget: checks run as one
consolidated job, emulator-bound integration tests are `workflow_dispatch` only, and
housekeeping crons run monthly.

Commit messages, pull request descriptions, code, and documentation MUST NOT contain
AI-assistant attribution of any kind — no `Co-Authored-By` trailer, no "Generated with"
line, no tool watermark. This overrides any contrary tooling default.

## Governance

This constitution supersedes conflicting practice elsewhere in the repository. Where it
and a `docs/guidelines/` document disagree, this document wins and the guideline MUST be
corrected.

**Amendment procedure.** Amendments MUST be made by editing this file, with a Sync Impact
Report at its head recording the version change, the sections added, modified, or removed,
and any deferred TODOs. An amendment that changes a principle MUST state what it replaces
and why.

**Versioning policy.** This document is versioned with semantic versioning:
- **MAJOR** — a principle is removed or redefined in a backward-incompatible way.
- **MINOR** — a principle or section is added, or guidance is materially expanded.
- **PATCH** — clarification, wording, or typo fixes that do not change obligations.

**Compliance review.** Every pull request MUST be checked against these principles before
merge. A deviation MUST be justified in writing in the PR description and either accepted
as a recorded exception or fixed before merge. Unjustified complexity MUST be rejected.

Runtime development guidance for agents and contributors lives in `CLAUDE.md` and
`docs/guidelines/`; both are subordinate to this constitution.

**Version**: 1.0.0 | **Ratified**: 2026-09-28 | **Last Amended**: 2026-09-28
