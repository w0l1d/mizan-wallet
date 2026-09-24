#!/usr/bin/env bash
#
# Tag the current commit with the version a build of it produces.
#
# The version is a pure function of the commit (see app/build.gradle.kts), so
# this script can derive the very string that is already baked into the APK.
# That is why tagging happens AFTER a successful build rather than before it:
# a build that fails leaves no tag behind to clean up.
#
#   scripts/tag-build.sh                  # create the tag locally
#   scripts/tag-build.sh --push           # ...and push it to the remote
#   scripts/tag-build.sh --version X      # tag a version read from elsewhere
#                                         #   (CI passes the APK's own value)
#   scripts/tag-build.sh --print          # print the version, tag nothing
#
# Idempotent: if the commit already carries this tag, it says so and exits 0.

set -euo pipefail

REMOTE="${TAG_REMOTE:-origin}"
PUSH=false
PRINT_ONLY=false
VERSION=""
IS_RELEASE=false

while [ $# -gt 0 ]; do
  case "$1" in
    --push)    PUSH=true ;;
    --print)   PRINT_ONLY=true ;;
    --version) VERSION="${2:?--version needs a value}"; shift ;;
    --remote)  REMOTE="${2:?--remote needs a value}"; shift ;;
    -h|--help) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

die() { echo "error: $*" >&2; exit 1; }

# --- refuse anything that cannot be named by a tag -------------------------
# --print is read-only, so it stays useful on a dirty tree.

if [ "$PRINT_ONLY" = false ] && [ -n "$(git status --porcelain)" ]; then
  die "working tree is dirty. A tag names a commit, so uncommitted changes cannot be published. Commit or stash first."
fi

# --- work out the version --------------------------------------------------

if [ -z "$VERSION" ]; then
  SHORT_SHA=$(git rev-parse --short HEAD)

  # Release tags are `v<date>-<code>`; beta tags are the beta version string.
  # Matching on `v*` keeps a beta tag from being mistaken for a release.
  if [ -n "$(git tag --points-at HEAD --list 'v*')" ]; then
    IS_RELEASE=true
    VERSION=$(grep 'version-name =' gradle/libs.versions.toml | awk -F'=' '{print $2}' | tr -d ' "')
  fi

  LAST_RELEASE_TAG=$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)
  if [ -n "$LAST_RELEASE_TAG" ]; then
    COMMITS_SINCE=$(git rev-list --count "$LAST_RELEASE_TAG..HEAD")
  else
    COMMITS_SINCE=$(git rev-list --count HEAD)
  fi

  if [ "$IS_RELEASE" = false ]; then
    COMMIT_DATE=$(git log -1 --format=%cd --date=format:%Y.%m.%d)
    VERSION="$COMMIT_DATE-beta.$COMMITS_SINCE+$SHORT_SHA"
  fi
fi

if [ "$PRINT_ONLY" = true ]; then
  echo "$VERSION"
  exit 0
fi

case "$VERSION" in
  *.dirty) die "refusing to tag '$VERSION' - it was built from an uncommitted tree." ;;
esac

# --- create the tag --------------------------------------------------------

if [ "$IS_RELEASE" = true ]; then
  echo "Commit is a RELEASE ($(git tag --points-at HEAD --list 'v*' | head -1)),"
  echo "version $VERSION - already tagged by the release workflow. Nothing to do."
  exit 0
fi

if [ "$(git rev-parse -q --verify "refs/tags/$VERSION" || true)" = "$(git rev-parse HEAD)" ]; then
  echo "Already tagged: $VERSION"
elif git rev-parse -q --verify "refs/tags/$VERSION" >/dev/null; then
  die "tag '$VERSION' already exists but points at a different commit."
else
  git tag -a "$VERSION" -m "Beta build $VERSION"
  echo "Created tag: $VERSION"
fi

if [ "$PUSH" = true ]; then
  git push "$REMOTE" "refs/tags/$VERSION"
  echo "Pushed $VERSION to $REMOTE"
else
  echo "Not pushed. To share this build: git push $REMOTE $VERSION"
fi
