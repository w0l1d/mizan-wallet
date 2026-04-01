#!/usr/bin/env bash
# =============================================================================
# Full Namespace Rebrand: com.ivy → dev.w0l1d.mizan
# =============================================================================
# This script performs a complete namespace rename across the entire codebase.
# It changes every package declaration, import, build config, manifest, and
# directory from the original com.ivy.* to dev.w0l1d.mizan.*.
#
# PREREQUISITES:
#   - Clean working tree (git status clean, or at least committed)
#   - Run from the repo root: bash scripts/full-rebrand.sh
#   - After running: ./gradlew clean assembleDebug to verify the build
#
# WHAT IT DOES:
#   1. Verify clean git state
#   2. applicationId change          (already done - dev.w0l1d.mizan in app/build.gradle.kts)
#   3. namespace in build.gradle.kts (all 40+ modules)
#   4. Package declarations/imports  (all .kt / .java / .xml / .kts files)
#   5. AndroidManifest package attr  (covered by step 4 sed)
#   6. Room schema directory rename  (com.ivy.data.db → dev.w0l1d.mizan.data.db)
#   7. Source directory rename       (com/ivy/ → dev/w0l1d/mizan/ in every module)
#   8. Fix user-facing strings       (Ivy → Mizan in strings.xml)
#
# OPTIONS:
#   --dry-run    Show what would change without modifying files
#   --force      Skip clean git state check
#
# UNDO: reverse the sed (swap OLD and NEW below), rename directories back,
#       and restore the schema folder name.
# =============================================================================

set -euo pipefail

OLD="com.ivy"
NEW="dev.w0l1d.mizan"
OLD_PATH="com/ivy"
NEW_PATH="dev/w0l1d/mizan"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

DRY_RUN=false
FORCE=false

for arg in "$@"; do
  case $arg in
    --dry-run) DRY_RUN=true ;;
    --force)   FORCE=true ;;
    *)         echo "Unknown option: $arg"; exit 1 ;;
  esac
done

# =============================================================================
# Step 0: Verify clean git state
# =============================================================================
if [ "$FORCE" = false ]; then
  echo "=== Step 0: Verifying clean git state ==="
  if [ -n "$(git -C "$ROOT" status --porcelain)" ]; then
    echo "ERROR: Working tree is not clean. Commit or stash changes first."
    echo "       Use --force to skip this check."
    exit 1
  fi
  echo "    Working tree is clean."
fi

# =============================================================================
# Step 1: applicationId — already done
# =============================================================================
echo ""
echo "=== Step 1: applicationId — already done in app/build.gradle.kts ==="
echo "    applicationId = \"$NEW\""

# =============================================================================
# Step 2 + 3: Text replacement in all source files
# =============================================================================
echo ""
echo "=== Step 2 + 3: Text replacement in all source files ==="

SOURCE_FILES=$(find "$ROOT" -type f \( -name "*.kt" -o -name "*.java" -o -name "*.kts" -o -name "*.xml" \) \
  -not -path "*/build/*" \
  -not -path "*/.gradle/*" \
  -not -path "*/.git/*" \
  -not -path "*/scripts/*")

FILE_COUNT=$(echo "$SOURCE_FILES" | wc -l | tr -d ' ')

if [ "$DRY_RUN" = true ]; then
  MATCH_COUNT=$(echo "$SOURCE_FILES" | xargs grep -l "${OLD//./\\.}\." 2>/dev/null | wc -l | tr -d ' ')
  echo "    [DRY RUN] Would update $MATCH_COUNT files (out of $FILE_COUNT total source files)"
  echo "    Pattern: s/${OLD//./\\.}\./${NEW//./\\.}./g"
else
  echo "$SOURCE_FILES" | xargs sed -i '' "s/${OLD//./\\.}\./${NEW//./\\.}./g"
  echo "    Done — $FILE_COUNT source files processed"
fi

# =============================================================================
# Step 4: Room schema directory rename
# =============================================================================
echo ""
echo "=== Step 4: Room schema directory rename ==="
SCHEMA_OLD="$ROOT/shared/data/core/schemas/${OLD}.data.db.IvyRoomDatabase"
SCHEMA_NEW="$ROOT/shared/data/core/schemas/${NEW}.data.db.IvyRoomDatabase"
if [ -d "$SCHEMA_OLD" ]; then
  if [ "$DRY_RUN" = true ]; then
    echo "    [DRY RUN] Would rename: ${OLD}.data.db.IvyRoomDatabase → ${NEW}.data.db.IvyRoomDatabase"
  else
    mv "$SCHEMA_OLD" "$SCHEMA_NEW"
    echo "    Renamed: ${OLD}.data.db.IvyRoomDatabase → ${NEW}.data.db.IvyRoomDatabase"
  fi
else
  echo "    Schema dir already renamed or not found — skipping"
fi

# =============================================================================
# Step 5: Rename source directories com/ivy → dev/w0l1d/mizan
# =============================================================================
echo ""
echo "=== Step 5: Rename source directories com/ivy → dev/w0l1d/mizan ==="
find "$ROOT" -type d -name ivy -path "*/$OLD_PATH" \
  -not -path "*/build/*" \
  -not -path "*/.gradle/*" \
  -not -path "*/.git/*" \
  | while read -r ivy_dir; do
      com_dir=$(dirname "$ivy_dir")          # …/src/main/java/com
      base_dir=$(dirname "$com_dir")         # …/src/main/java
      new_ivy="$base_dir/$NEW_PATH"          # …/src/main/java/dev/w0l1d/mizan

      if [ "$DRY_RUN" = true ]; then
        echo "    [DRY RUN] Would move: $ivy_dir → $new_ivy"
      else
        mkdir -p "$new_ivy"
        find "$ivy_dir" -mindepth 1 -maxdepth 1 | while read -r item; do
          dest="$new_ivy/$(basename "$item")"
          if [ -d "$item" ]; then
            cp -R "$item" "$dest"
          else
            cp "$item" "$dest"
          fi
        done
        rm -rf "$com_dir"   # removes com/ivy (and the now-empty com/ dir)
        echo "    Moved: $ivy_dir → $new_ivy"
      fi
  done

# =============================================================================
# Step 6: Fix user-facing strings (Ivy → Mizan)
# =============================================================================
echo ""
echo "=== Step 6: Fix user-facing strings ==="
STRINGS_FILES=$(find "$ROOT/shared/ui/core/src/main/res" -name "strings.xml" 2>/dev/null || true)

if [ -n "$STRINGS_FILES" ]; then
  if [ "$DRY_RUN" = true ]; then
    MATCH_COUNT=$(echo "$STRINGS_FILES" | xargs grep -l "Ivy" 2>/dev/null | wc -l | tr -d ' ')
    echo "    [DRY RUN] Would fix Ivy → Mizan in $MATCH_COUNT strings.xml files"
  else
    echo "$STRINGS_FILES" | xargs perl -pi -e 's/\bIvy Wallet\b/Mizan Wallet/g'
    echo "$STRINGS_FILES" | xargs perl -pi -e 's/\bIvy Team\b/Mizan Team/g'
    echo "$STRINGS_FILES" | xargs perl -pi -e 's/\bIvy\b/Mizan/g'
    # Fix double-name from previous replacements
    echo "$STRINGS_FILES" | xargs perl -pi -e 's/Mizan Wallet Wallet/Mizan Wallet/g'
    echo "    Done — fixed Ivy → Mizan in strings.xml files"
  fi
else
  echo "    No strings.xml files found — skipping"
fi

# =============================================================================
# Done
# =============================================================================
echo ""
echo "=== Done! ==="
echo ""
echo "Next steps:"
echo "  1. ./gradlew clean"
echo "  2. ./gradlew assembleDebug"
echo "  3. Fix any remaining compilation errors (Hilt component names, R class refs)"
echo "  4. ./gradlew test"
echo "  5. ./gradlew detekt"
echo ""
echo "KNOWN POST-RENAME ISSUES TO FIX MANUALLY:"
echo "  - IvyRoomDatabase.kt: schemaLocation path string still references old dir name"
echo "    Search for 'schemas' in shared/data/core/build.gradle.kts and update the path"
echo "  - Any hardcoded string literals in test code that spell out 'com.ivy'"
echo "  - Firebase/Crashlytics google-services.json references applicationId"
echo "    (update google-services.json with new applicationId dev.w0l1d.mizan)"
echo "  - Paparazzi golden screenshots may need regeneration after package rename"
