# Mizan Wallet — Project Backlog

**Updated:** 2026-08-03
**Branching model:** `feat/*` → `develop` → `main`
**Repo:** [w0l1d/mizan-wallet](https://github.com/w0l1d/mizan-wallet)

---

## ✅ Done (awaiting merge)

| ID | Task | Status | Branch | PR | Note |
|----|------|--------|--------|----|------|
| MZ-001 | Excluded-transfer income/expense toggles | ✅ merged to develop | `feat/transfer-excluded-calc` | — | [note](notes/mz-001-transfer-excluded.md) |
| MZ-002 | Accounts screen date/period filter | ✅ merged to develop | `feat/accounts-date-filter` | — | [note](notes/mz-002-accounts-filter.md) |
| MZ-003 | Split balance / income-expense ranges | ✅ on develop | — | — | [note](notes/mz-003-split-ranges.md) |
| MZ-004 | CI: auto-bump version, auto-tag, APK in Telegram | ✅ on develop | — | — | [note](notes/mz-004-ci-improvements.md) |
| MZ-005 | Integration PR (`develop → main`) | 🚧 open, 2 red checks fixed | — | [#8](https://github.com/w0l1d/mizan-wallet/pull/8) | `pr-description-check` ✅; `test` + `integration_test` fixed by MZ-007/008 |
| MZ-006 | Enhanced Telegram APK messages (PR context + commit details) | ✅ on develop | — | — | [note](notes/mz-ci-01-telegram-enhance.md); commit `cc6de009` |
| MZ-007 | Fix `integration_test` `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | ✅ on develop | — | — | Cached AVD userdata + regenerated debug keystore → uninstall `com.ivy.*` before install |
| MZ-008 | Fix flaky property tests (`UncompletedCoroutinesError`) | ✅ on develop | — | — | `runTest` 60s default too low on loaded runners → `PropertyTestTimeout = 5.minutes` |

---

## 🚧 In Progress

| ID | Task | Status | Branch | Next |
|----|------|--------|--------|------|
| — | *(none actively being developed)* | | | |

---

## 📋 Backlog

### 🐛 Bugs

| ID | Task | Priority | Notes |
|----|------|----------|-------|
| MZ-BUG-01 | ~~PR #8: `pr-description-check` CI failing~~ → **FIXED** (`Closes N/A` added, PR reopened) | ✅ done | |

### 🔧 CI / DevOps

| ID | Task | Priority | Notes |
|----|------|----------|-------|
| MZ-CI-01 | ~~Enhance Telegram APK message with PR context, commit info, SHA-256~~ → **DONE** (`cc6de009`) | ✅ done | [note](notes/mz-ci-01-telegram-enhance.md) |
| MZ-CI-02 | Adopt `concurrency` groups in release workflows to serialize runs (latch pattern) | 🟢 low | |
| MZ-CI-03 | Investigate per-ABI APK splitting if fat APK approaches Telegram's 50 MB limit | 🟢 low | |
| MZ-CI-04 | Switch tag format to `vMAJOR.MINOR.PATCH-YYYY.MM.DD.BUILD` (latch pattern) | 🟢 low | Current format: `vYYYY.MM.DD-CODE` |
| MZ-CI-05 | Tag-as-source-of-truth: stop committing version bumps to `main` (latch pattern) | 🟢 low | Eliminates `[skip ci]` commits and fast-forward races |

### 🟣 Features

| ID | Task | Priority | Notes |
|----|------|----------|-------|
| MZ-FEA-01 | Rebase & revive `feat/zakat-tracking` | 🟡 medium | Stale branch, last merged from `main`; needs conflict resolution |
| MZ-FEA-02 | Rebase & revive `feat/rebrand-mizan-logo` | 🟡 medium | Stale branch, last merged from `main` |
| MZ-FEA-03 | Proper per-feature PRs into `develop` (one feature per PR) | 🟡 medium | Prevents the "commits on develop directly" problem that caused stale branches |

### 🔄 Refactoring

| ID | Task | Priority | Notes |
|----|------|----------|-------|
| MZ-REF-01 | Migrate `temp/` legacy code into proper shared modules | 🟢 low | Ongoing debt from original ivy-wallet fork |
| MZ-REF-02 | Replace deprecated `actions/create-release@v1.1.4` with `gh release create` | 🟢 low | Used in `internal_release.yml` |

---

## Legend

- 🔴 high — blocking or urgent
- 🟡 medium — next in queue
- 🟢 low — nice to have

## Task ID Format

`MZ-{TYPE}-{NN}` — TYPE is BUG, CI, FEA (feature), or REF (refactoring).
