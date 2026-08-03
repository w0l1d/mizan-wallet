# MZ-004 — CI Improvements (auto-bump, auto-tag, APK in Telegram)

**Status:** ✅ Done (on `develop`)
**Commits:** `705d6af2`, `8e829ed3`, `ca86b7ca`, `6d36d41e`
**Date:** 2026-07

## What was done

| Commit | Change |
|--------|--------|
| `6d36d41e` | Folded weekly version bump into `apk.yml`; automated production release trigger |
| `ca86b7ca` | Auto-bump app version on every `main` merge; CI validation in `apk.yml` |
| `705d6af2` | Auto-tag on `main` merge; send APK to Telegram on tag creation |
| `8e829ed3` | Fixed dependency-audit workflow YAML syntax + period display label |

## Current version scheme

- **Source:** `gradle/libs.versions.toml` → `version-name = "2025.07.17"`, `version-code = "206"`
- **Tag format:** `vYYYY.MM.DD-CODE` (e.g. `v2025.07.17-206`)
- **Auto-bump on `main` push:** increments `version-code`, sets `version-name` to today's date, commits back to `main` with `[skip ci]`, then pushes tag
