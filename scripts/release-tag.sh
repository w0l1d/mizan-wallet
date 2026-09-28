#!/usr/bin/env bash
#
# The release-tag pattern, in one place.
#
# A release tag is `v<YYYY>.<MM>.<DD>-<version-code>`, e.g. v2025.07.17-206.
# It is written by the auto_tag job in .github/workflows/apk.yml, or by hand.
#
# Everything that has to decide "is this commit a release?" MUST ask this
# script rather than globbing for `v*`. `v*` is not the pattern:
#
#   - 81 of this repo's 115 `v*` tags are upstream Ivy Wallet's abandoned
#     semver scheme (v4.6.7-168, v2.2.10-comet, v4.3.14-attempt2). Those are
#     not releases of this fork and must never be treated as one.
#   - `v*` also swallows any scratch tag a human leaves behind - `vtest`,
#     `verify-me`. One of those on a develop commit silently flips the build
#     to the release channel, which then dies on a confusing version mismatch.
#   - `git describe --match 'v*'` picks the nearest such tag to count commits
#     since the last release, so a stray tag corrupts the beta version code.
#
#   scripts/release-tag.sh --regex        # the ERE, for validation
#   scripts/release-tag.sh --glob         # the glob, for `git describe --match`
#   scripts/release-tag.sh --check TAG    # exit 0 iff TAG is well formed
#   scripts/release-tag.sh --at [REV]     # release tags on REV (default HEAD)
#   scripts/release-tag.sh --last [REV]   # nearest release tag reachable from REV
#   scripts/release-tag.sh --expected     # the tag this commit's toml requires

set -euo pipefail

# Month and day are range-checked, so v2025.13.45-1 is rejected rather than
# quietly accepted and then baked into a version nobody can parse back.
REGEX='^v[0-9]{4}\.(0[1-9]|1[0-2])\.(0[1-9]|[12][0-9]|3[01])-[0-9]+$'

# git and GitHub Actions tag filters take globs, not regexes, so the pattern
# also exists in this weaker form. It cannot range-check month and day; it is a
# pre-filter only, and anything that makes a decision must still use $REGEX.
GLOB='v[0-9][0-9][0-9][0-9].[0-9][0-9].[0-9][0-9]-[0-9]*'

die() { echo "error: $*" >&2; exit 1; }

case "${1:---regex}" in
  --regex) echo "$REGEX" ;;
  --glob)  echo "$GLOB" ;;

  --check)
    TAG="${2:?--check needs a tag}"
    if ! echo "$TAG" | grep -Eq "$REGEX"; then
      die "'$TAG' is not a release tag. Expected v<YYYY>.<MM>.<DD>-<version-code>, e.g. v2025.07.17-206."
    fi
    ;;

  # Exact-pattern replacement for `git tag --points-at REV --list 'v*'`.
  # Prints nothing (and still exits 0) when the commit carries no release tag,
  # so callers can test with -n/-z.
  --at)
    git tag --points-at "${2:-HEAD}" | grep -E "$REGEX" || true
    ;;

  --last)
    git describe --tags --abbrev=0 --match "$GLOB" "${2:-HEAD}" 2>/dev/null || true
    ;;

  # The tag this commit MUST carry to be a release: its toml version, not the
  # date it is built on. Lets CI check a hand-pushed tag against the APK.
  --expected)
    F=gradle/libs.versions.toml
    NAME=$(grep 'version-name =' "$F" | awk -F'=' '{print $2}' | tr -d ' "')
    CODE=$(grep 'version-code =' "$F" | awk -F'=' '{print $2}' | tr -d ' "')
    echo "v${NAME}-${CODE}"
    ;;

  -h|--help) sed -n '2,28p' "$0" | sed 's/^# \{0,1\}//' ;;
  *) die "unknown argument: $1" ;;
esac
