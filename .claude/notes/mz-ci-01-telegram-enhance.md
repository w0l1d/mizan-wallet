# MZ-CI-01 — Enhance Telegram APK Message

**Status:** 📋 Backlog
**Priority:** 🟡 medium
**Reference:** latch `.github/workflows/release.yml` lines 226-307

## Problem

Mizan's Telegram messages (both `apk.yml` and `internal_release.yml`) are minimal:

```
📦 Mizan Wallet Demo APK

<commit message first 100 chars>

🔖 Commit: <sha>
🔗 <commit link>
🔐 SHA-256: <hash>
```

No PR context, no commit author, no release link, no tag info (in some paths).

## What latch does

Latch's `Announce in Telegram` step:

1. **Gathers release context** — commit subject, full body (truncated to 400 chars), author
2. **Queries associated PR** — uses `gh api repos/.../commits/<sha>/pulls` to find the PR whose merge/squash produced this commit; extracts PR number, title, URL, branch
3. **Self-describing caption:**
   - 🚀 App name + version tag + release date
   - 📝 Commit subject
   - 📄 Commit body (truncated to 400 chars)
   - 🔀 PR #N: title (if exists)
   - 🌿 Branch name (if PR exists)
   - 🔗 PR URL (if PR exists)
   - 🔖 Commit SHA + author
   - 🔗 Commit link
   - 📋 Release link
   - 🔐 SHA-256 hashes
4. **Sends two per-ABI APKs** — first message has full caption; second is terse ("📦 filename")

## Recommended changes for Mizan

### `apk.yml` — demo APK upload step (lines 131-151)

Replace the current caption with:

```yaml
- name: Gather release context
  id: context
  env:
    GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  run: |
    COMMIT_SUBJECT=$(git log -1 --pretty=%s)
    COMMIT_AUTHOR=$(git log -1 --pretty=%an)
    echo "commit_subject=$COMMIT_SUBJECT" >> "$GITHUB_OUTPUT"
    echo "commit_author=$COMMIT_AUTHOR" >> "$GITHUB_OUTPUT"
    {
      echo "commit_body<<BODY_EOF"
      git log -1 --pretty=%b
      echo "BODY_EOF"
    } >> "$GITHUB_OUTPUT"

    PR_JSON=$(gh api "repos/${GITHUB_REPOSITORY}/commits/${GITHUB_SHA}/pulls" 2>/dev/null || echo '[]')
    echo "pr_number=$(echo "$PR_JSON" | jq -r '.[0].number // empty')" >> "$GITHUB_OUTPUT"
    echo "pr_title=$(echo "$PR_JSON" | jq -r '.[0].title // empty')" >> "$GITHUB_OUTPUT"
    echo "pr_url=$(echo "$PR_JSON" | jq -r '.[0].html_url // empty')" >> "$GITHUB_OUTPUT"
    echo "pr_branch=$(echo "$PR_JSON" | jq -r '.[0].head.ref // empty')" >> "$GITHUB_OUTPUT"

- name: Announce in Telegram
  env:
    COMMIT_BODY: ${{ steps.context.outputs.commit_body }}
  run: |
    # Build enriched caption (see full template in latch release.yml lines 280-291)
    # ... enhanced caption with PR info, release link, author, body excerpt
```

### `internal_release.yml` — Telegram announce step (lines 168-181)

Same enrichment pattern, plus add the release URL (already computed as `RELEASE_URL` — just needs to be included in caption).

## Files to change

| File | Change |
|------|--------|
| `.github/workflows/apk.yml` | Lines 131-151 — enriched Telegram caption |
| `.github/workflows/internal_release.yml` | Lines 168-181 — enriched Telegram caption |
