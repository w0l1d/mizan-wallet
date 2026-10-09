# Phase 4 — Package Rename Plan: `com.ivy.*` → `dev.w0l1d.mizan.*`

Status: **planning only, not yet executed**. Authored: 2026-04-18.

Target applicationId already set at `app/build.gradle.kts:19` → `dev.w0l1d.mizan`.
This plan aligns the source-code namespaces with that applicationId.

---

## 1. Scope (measured against current tree)

| Surface | Count | Notes |
|---|---|---|
| `com/ivy/` source roots | 58 directories | `find . -path "*/com/ivy" -type d` (excluding `build/`) |
| Kotlin/Gradle files referencing `com.ivy` | 824 | `grep -rln 'com\.ivy' --include="*.kt" --include="*.kts"` |
| Gradle `namespace = "com.ivy…"` declarations | 40 | across `**/build.gradle.kts` |
| AndroidManifest files with `com.ivy` | 2 | `app/src/main/AndroidManifest.xml`, `shared/data/core/src/main/AndroidManifest.xml` |
| Paparazzi snapshot PNGs (FQN in filename) | 52 | `src/test/snapshots/images/com.ivy.*.png` |
| Room exported schema | 1 | `shared/data/core/schemas/com.ivy.data.db.IvyRoomDatabase/` |
| Widget `<receiver>` entries (Android-persisted FQN) | 3 | `AddTransactionWidget`, `AddTransactionWidgetCompact`, `WalletBalanceWidgetReceiver` |
| `@Serializable` entities (wire-format risk) | 10 DB entities | under `shared/data/core/src/main/java/com/ivy/data/db/entity/` |
| `buildSrc` precompiled plugin IDs (`ivy.feature`, etc.) | 12 | 54 modules apply them |
| `temp/` (de-scoped) | `temp/legacy-code`, `temp/old-design` | keep `com.ivy.*`, will be deleted during legacy migration |

---

## 2. Name mapping

| From | To |
|---|---|
| `com.ivy.base` | `dev.w0l1d.mizan.base` |
| `com.ivy.wallet` (the `app/` module) | `dev.w0l1d.mizan.app` |
| `com.ivy.data`, `com.ivy.data.model` | `dev.w0l1d.mizan.data`, `dev.w0l1d.mizan.data.model` |
| `com.ivy.domain` | `dev.w0l1d.mizan.domain` |
| `com.ivy.ui`, `com.ivy.navigation` | `dev.w0l1d.mizan.ui`, `dev.w0l1d.mizan.navigation` |
| `com.ivy.zakat` | `dev.w0l1d.mizan.zakat` |
| `com.ivy.widget.*` | `dev.w0l1d.mizan.widget.*` (with compat shims — §5) |
| all other `com.ivy.<feature>` | `dev.w0l1d.mizan.<feature>` |
| `temp/legacy-code`, `temp/old-design` | **no change** (de-scoped) |

Plugin IDs (`ivy.feature`, `ivy.compose`, …): **keep as-is** for this phase. They are internal Gradle plugin identifiers, not public FQNs, and renaming them multiplies churn across 54 `build.gradle.kts` files with no user-visible gain. Optional follow-up.

---

## 3. Risk register (each item is blocking until addressed)

### R1 — Pinned widgets break on upgrade (HIGH)
Android persists `ComponentName(package, className)` for widgets the user has pinned to their home screen. Renaming the provider class FQN orphans every widget already on a device. No platform `<receiver-alias>` exists.
**Mitigation**: see §5, back-compat shim strategy.
**Detection**: CI cannot catch this — requires manual smoke test (§7, gate G7).

### R2 — Kotlinx Serialization wire format (HIGH)
`@Serializable` classes without explicit `@SerialName` use the FQN as default discriminator in polymorphic / sealed hierarchies. Every one of the 10 DB entities is `@Serializable`. If any serialized payload (persisted JSON, DataStore, Firestore, backup-export) embeds FQNs, renaming corrupts existing user data.
**Mitigation**:
1. Audit every `@Serializable` for polymorphic usage and explicit `@SerialName` annotations.
2. For any discriminator that was previously implicit, pin it to the old FQN with `@SerialName("com.ivy.…")` **before** renaming.
3. Grep for `Json { classDiscriminator = …; useArrayPolymorphism = …; serializersModule }` to confirm no FQN-dependent modules.
**Detection**: data round-trip tests before/after rename on a sample backup file.

### R3 — Room schema path (MEDIUM)
`shared/data/core/schemas/com.ivy.data.db.IvyRoomDatabase/` is keyed by the DB class FQN. Renaming moves the schema file path. The JSON contents only care about SQL, but the path and CI golden checks must move in lockstep.
**Mitigation**: `git mv` the schema directory in the same commit as the class rename; do not allow `ksp` to regenerate without the move.
**Detection**: `./gradlew :shared:data:core:assembleDebug` — Room's exportSchema verifier fails loudly.

### R4 — Paparazzi golden files reference FQNs (MEDIUM)
52 PNGs under `src/test/snapshots/images/` embed the test FQN (e.g., `com.ivy.ui.component_OpenSourceCardPaparazziTest_…png`). After rename these filenames must change.
**Mitigation**: rename the files with `git mv` using a scripted transform from `com.ivy.` → `dev.w0l1d.mizan.` in the filename. Do **not** simply re-record baselines — that would hide real visual regressions.
**Detection**: `./gradlew verifyPaparazziDebug` passes, and `git diff` shows only filename changes, zero binary-content changes.

### R5 — FileProvider / authority / deep-link strings (LOW)
No `com.ivy`-prefixed authorities or deep links were found in the current tree (confirmed via grep over manifests and Kotlin). Worth re-checking right before execution in case something lands on main first.

### R6 — Hilt/KSP generated code (LOW, self-healing)
Hilt/Room/Dagger generated classes regenerate automatically from the new package. Expected to "just work" once compilation succeeds.

### R7 — Reflection / `::class.qualifiedName` (LOW)
Grep for `qualifiedName`, `::class.java.name`, `Class.forName` returned **zero hits** in project code (excluding `temp/`). Re-verify before execution.

### R8 — `applicationId` suffix collisions (LOW)
The `app/build.gradle.kts` sets `applicationId = "dev.w0l1d.mizan"`. Some manifest references use `${applicationId}.androidx-startup`. No change needed; the placeholder resolves correctly.

---

## 4. Execution order (one commit per step; stop and fix at failures)

Each step ends with a gate from §7. Do not advance until green.

1. **Audit `@Serializable` discriminators** — add explicit `@SerialName("com.ivy.…")` to every polymorphic type that relied on implicit FQN. Commit. Gate G0.
2. **Rename in `buildSrc/`**: no `com.ivy` found; skip. Verify with `grep -r "com\.ivy" buildSrc/`.
3. **`namespace` in every module `build.gradle.kts`**: sed `namespace = "com.ivy` → `namespace = "dev.w0l1d.mizan` (40 files). Commit. Gate G1.
4. **Move source directories** per module: `git mv src/main/java/com/ivy src/main/java/dev/w0l1d/mizan` (analogously for `src/test/java`, `src/androidTest/java`, `src/main/kotlin`). Do not bulk-move across modules — keep each `git mv` scoped to one module so diffs are reviewable.
5. **Rewrite `package` and `import` statements**: two scripted passes over `**/*.kt` and `**/*.kts` (excluding `temp/` and `build/`):
   - `s/^package com\.ivy/package dev.w0l1d.mizan/`
   - `s/\bimport com\.ivy\./import dev.w0l1d.mizan./`
   - `s/\bcom\.ivy\./dev.w0l1d.mizan./` (for FQN uses inside code, annotations, etc.)
   Run against `temp/` exclusion list. Commit. Gate G2.
6. **`AndroidManifest.xml` edits**:
   - `app/src/main/AndroidManifest.xml` — `package="com.ivy.wallet"` → `package="dev.w0l1d.mizan.app"`; widget `<receiver android:name="com.ivy.widget.…">` left pointing at the shim path (§5); add new `<receiver>` entries for the renamed classes.
   - `shared/data/core/src/main/AndroidManifest.xml` — `package="com.ivy.data"` → `package="dev.w0l1d.mizan.data"`.
   Commit. Gate G3.
7. **Widget back-compat shims** (§5). Commit. Gate G4.
8. **Room schema**: `git mv shared/data/core/schemas/com.ivy.data.db.IvyRoomDatabase shared/data/core/schemas/dev.w0l1d.mizan.data.db.IvyRoomDatabase` in the same commit as step 6. Gate G3 covers this.
9. **Paparazzi goldens**: script a rename of all 52 PNG filenames from `com.ivy.` prefix to `dev.w0l1d.mizan.` prefix under every `src/test/snapshots/images/` directory. Commit. Gate G5.
10. **Temp carve-out verification**: confirm `grep -r "com\.ivy" app/ shared/ feature/ widget/ buildSrc/` returns **empty** and `grep -r "com\.ivy" temp/` still has hits (by design). Gate G6.
11. **Manual widget smoke test** against the last pre-rename APK. Gate G7.

---

## 5. Widget back-compat strategy

Problem: three widget providers are persisted by FQN on user devices:
- `com.ivy.widget.transaction.AddTransactionWidget`
- `com.ivy.widget.transaction.AddTransactionWidgetCompact`
- `com.ivy.widget.balance.WalletBalanceWidgetReceiver`

Android has no `<receiver-alias>`. The robust pattern:

1. Move implementation to the new FQN, e.g. `dev.w0l1d.mizan.widget.transaction.AddTransactionWidget` — this is what step 4-5 above produces.
2. Keep a **deprecated empty subclass** at each legacy FQN that extends the new class — kept at the old source path:
   ```kotlin
   // widget/add-transaction/src/main/java/com/ivy/widget/transaction/AddTransactionWidget.kt
   package com.ivy.widget.transaction

   @Deprecated(
       "Back-compat shim for pre-rename widget placements; " +
       "real implementation moved to dev.w0l1d.mizan.widget.transaction.AddTransactionWidget"
   )
   class AddTransactionWidget : dev.w0l1d.mizan.widget.transaction.AddTransactionWidget()
   ```
   Note: widget providers are Android components instantiated by the system. The empty subclass inherits all behavior and the provider XML metadata, so this is safe.
3. In `app/AndroidManifest.xml`, keep the existing `<receiver android:name="com.ivy.widget.…">` entries unchanged **and** add new entries pointing at `dev.w0l1d.mizan.widget.…`. Both share the same `@xml/…_widget_info` metadata. New placements use the new FQN; old placements keep working via the shim.
4. The shims are pure glue — exclude them from the bulk `sed` rewrite in step 5. Use a targeted exclusion like `find … -path "*/widget/*" -name "*.kt" | … | grep -v "back-compat-shims-list.txt"`.

Retention policy: keep shims for ≥2 major releases, then remove once widget re-placement rate is verified negligible via telemetry / crash reports.

---

## 6. Scripted transforms (reference, not yet run)

Kotlin/Gradle rewrite (excludes `temp/`, `build/`, `.gradle/`, `.git/`, the shim files listed in `back-compat-shims-list.txt`):
```bash
git ls-files '*.kt' '*.kts' \
  | grep -v -E '^(temp/|.*/build/|buildSrc/build/)' \
  | xargs -0 -I{} sh -c 'grep -lF "com.ivy" "$1" || true' _ {} \
  > files-to-rewrite.txt

while read -r f; do
  # perl preserves word boundaries and handles UTF-8 correctly
  perl -i -pe 's/\bcom\.ivy\./dev.w0l1d.mizan./g' "$f"
done < files-to-rewrite.txt
```

Paparazzi PNG rename:
```bash
find . -path "*/snapshots/images/*" -name "com.ivy.*.png" | while read -r f; do
  new=$(printf '%s' "$f" | sed 's|/com\.ivy\.|/dev.w0l1d.mizan.|')
  git mv "$f" "$new"
done
```

Room schema rename: single `git mv` (covered in step 8).

---

## 7. Verification gates (iterate until green before advancing)

| Gate | Command | Expected |
|---|---|---|
| G0 | `./gradlew :shared:data:core:testDebugUnitTest` after `@SerialName` additions | all serialization tests still green |
| G1 | `./gradlew build` after namespace rewrite (pre-move) | expected to **fail** because source is still at old path — this gate is a baseline snapshot, not a green requirement |
| G2 | `./gradlew clean build` after source move + import rewrite | compile green across all modules |
| G3 | `./gradlew :app:assembleDebug` after manifest + schema edits | APK builds, Room `exportSchema` verifies |
| G4 | `./gradlew assembleDebug` with shims in place | APK builds, both old and new receivers register |
| G5 | `./gradlew verifyPaparazziDebug` after PNG rename | zero diffs vs recorded baselines; no re-record |
| G6 | `./gradlew test` + `./gradlew detekt` | full unit suite green, lint clean |
| G7 | Manual: install pre-rename APK, pin all 3 widgets, upgrade-install post-rename APK | widgets continue to function without re-placement |

Hard rule: do not mask a failure with `--no-verify`, disabled tests, or re-recorded baselines. If a gate reveals a design issue (e.g., hidden FQN dependency), stop and raise it.

---

## 8. Rollback

Each step is a separate commit. Rollback = `git revert` the specific step's commit. The risky commits are G2 (source move) and G4 (widget shims); both are individually revertible. The Room schema move (G3) is paired with the DB class rename in the same commit so they revert together.

---

## 9. Deferred / out of scope

- `temp/legacy-code` and `temp/old-design` — keep `com.ivy.*`, handled by the legacy-migration workstream.
- `buildSrc/` plugin IDs (`ivy.feature`, etc.) — optional cosmetic follow-up, not functional.
- App-display-name / string resources that say "Ivy" — handled by the rebrand string-fix workstream, not this phase.
- Firebase project IDs, Play Console package name — `applicationId` already switched; no further action here.

---

## 10. Estimated effort

- Steps 1–2 (serialization audit, buildSrc verify): ~1 hour.
- Steps 3–5 (namespace + moves + rewrite): ~2 hours, largely scripted.
- Steps 6–9 (manifests, widget shims, Paparazzi): ~2 hours.
- Gate iteration (expect 1–3 rounds of compile fixes): ~2 hours.
- Manual widget smoke test: ~30 min.

**Total: roughly a full day of focused work.** The dominant risk is R2 (serialization); everything else is mechanical once the sed transform is proven on one module.
