# Contributing

How a change goes from an idea to a released version. Applies to humans and AI agents alike.

## 1. Roles and permissions

- **Human**: sets the task, reviews, merges, creates release tags, deploys.
- **Agent**: edits the working copy, runs tests, drafts commit messages and PR texts.
- Agent does **not** create branches, commit, push, open PRs or tag without an explicit request from the human.
- Exception: the human may explicitly ask the agent to take a change all the way to a test release (see [§10](#10-autonomous-mode-agent)).
- An approval covers only the action it was given for; it does not carry over to the next change.

## 2. Task intake

- A task starts from either:
  - a chat with the agent, or
  - a GitHub Issue.
- The task is recorded in:
  - `openspec/changes/<name>/proposal.md` — for changes that go through OpenSpec ([§3](#3-choose-the-track));
  - the Issue or the PR description — for everything else.
- Issue as the starting point: if the change is not small, create an OpenSpec proposal from the Issue description and continue with the OpenSpec track; link the Issue in the proposal.
- Unclear requirements → ask before writing code. Use `/opsx:explore` to think a problem through.

## 3. Choose the track

- **OpenSpec track** — required for:
  - new features;
  - large bug fixes that change specified behavior (anything that contradicts or extends `openspec/specs/`).
- **Lightweight track** — no OpenSpec, for:
  - refactoring;
  - small bug fixes that do not affect specs;
  - CI, build, dependencies;
  - docs, typos.
- In doubt → OpenSpec track.

## 4. Branching

- Default branch: `main`. No long-lived branches.
- Work branch: `<type>/<kebab-case-name>`, `<type>` from [§6](#6-commit-messages).
  - Example: `fix/detect-list-field-changes`, `feat/guard-against-broken-scraping`.
  - On the OpenSpec track the name matches the change name.
- Small lightweight-track changes (docs, diagrams, CI, build tweaks such as a Docker base image) go directly to `main`, without a branch or PR — by the human, or by the agent on the human's explicit request.
  - The commit contains only the files of that change; unrelated staged or modified work stays out (`git commit <paths>`).
  - A follow-up fix to such a commit is made with `git commit --amend`, not a new commit; if it was already pushed, push with `git push --force-with-lease`.

## 5. OpenSpec track

1. **Propose** — `/opsx:propose`: `proposal.md`, `design.md`, `specs/`, `tasks.md` in `openspec/changes/<name>/`.
   - Artifacts in English; project rules are in `openspec/config.yaml`.
2. **Review the plan** — human reviews artifacts; changes via `/opsx:update`.
3. **Apply** — `/opsx:apply`: implement `tasks.md`, check off tasks as done.
4. **Verify**:
   - every behavior change has a test (processing logic → integration test with HTML fixtures in `src/test/resources/web/<source>/`);
   - `./gradlew test` passes.
5. **Archive** — `/opsx:archive` in the **same branch, before merge**:
   - all tasks checked, tests green;
   - delta specs synced into `openspec/specs/`;
   - Mermaid diagrams in `## Overview` of touched specs updated.
6. `main` receives code and updated specs in one PR.

## 6. Commit messages

[Conventional Commits](https://www.conventionalcommits.org/):

```
<type>[(scope)][!]: <description>

[body]

[footer]
```

- **Types**:
  - `feat` — new user-visible behavior;
  - `fix` — bug fix;
  - `refactor` — no behavior change;
  - `test` — tests only;
  - `docs` — docs, specs, README;
  - `build` — Gradle, Dockerfile, dependencies;
  - `ci` — GitHub Actions;
  - `chore` — anything else (OpenSpec housekeeping, config).
- **Scope** (optional): affected area — `compare`, `telegram`, `template`, `scraping`, `migration`, `sg`, …
- **Description**:
  - English, imperative mood ("add", not "added"/"adds");
  - lowercase first letter, no trailing period;
  - whole subject line ≤ 72 chars.
- **Body** (optional for trivial changes):
  - separated by a blank line, wrapped at 72;
  - explains *what* and *why*, not *how*; bullets allowed.
- **Breaking change**: `!` after type/scope and a `BREAKING CHANGE: <what and how to migrate>` footer.
- No `Co-Authored-By` or other agent attribution.
- Example:

  ```
  fix(compare): detect list field changes

  CompareService kept only Javers ValueChange entries, so changes to
  list fields (e.g. participants) never triggered update notifications.
  It now collects every PropertyChange.
  ```

## 7. Pull requests

- One PR = one logical change.
- Merged only with **squash** (`gh pr merge --squash --delete-branch`); the work branch is deleted right after merge, both remote and local.
- **Title** = the squash commit subject, in Conventional Commits format ([§6](#6-commit-messages)).
- **Body** = the squash commit body:

  ```markdown
  ## Why
  <problem / motivation, 1–3 sentences>

  ## What changed
  - <behavior-level bullet>
  - <migration, config or spec changes, if any>

  ## Out of scope
  - <what is intentionally not done, and where it goes> (optional)
  ```

- Mention OpenSpec when used: `Specs: <spec names> (change archived)`.
- Started from an Issue → last line of the body: `Closes #N`.
- Call out explicitly: Mongock migrations, new/changed config properties or env vars, breaking changes.
- English. No agent attribution.
- Before requesting review:
  - CI (`./gradlew test`) is green;
  - OpenSpec change archived (OpenSpec track).

## 8. Versioning

- [SemVer](https://semver.org/), tag `vX.Y.Z`; the build takes the version from the git tag (`gitVersion()`), no version in files.
  - **MAJOR** — incompatible change of config or data with no automatic migration;
  - **MINOR** — new features;
  - **PATCH** — bug fixes only.
- Pre-release: `vX.Y.Z-rcN`, `N` starts at 1 and increments per new RC of the same version.

## 9. Release

Tags are created on `main` with green CI.

1. **Release candidate**
   - `git tag vX.Y.Z-rcN && git push origin vX.Y.Z-rcN`
   - `release.yml` tests and pushes `ghcr.io/viktor-235/sched-alert-bot:vX.Y.Z-rcN` (`:latest` is **not** moved).
   - Wait for the workflow to succeed, then:
     `gh release create vX.Y.Z-rcN --verify-tag --generate-notes --prerelease`
2. **Test stand** (human, unless delegated — [§10](#10-autonomous-mode-agent))
   - Deploy the RC image: set the image tag to `vX.Y.Z-rcN`, then `docker compose pull && docker compose up -d`.
   - Check:
     - app starts, Mongock migrations applied;
     - a scheduled run completes without errors in logs;
     - notifications look right in Telegram; `/status` responds.
   - Problem found → fix via PR → next `rcN+1`.
3. **Stable**
   - Tag the **same commit** as the verified RC:
     `git tag vX.Y.Z vX.Y.Z-rcN^{} && git push origin vX.Y.Z`
   - `release.yml` pushes `:vX.Y.Z` and moves `:latest`.
   - `gh release create vX.Y.Z --verify-tag --generate-notes --latest`
4. **Production** (human)
   - On the server: `docker compose pull && docker compose up -d`.
   - Check the same list as on the test stand.

## 10. Autonomous mode (agent)

Only when the human explicitly asks to take a change to a test release without their participation:

- Agent may: create the branch, commit, push, open the PR, merge it after green CI, tag `vX.Y.Z-rcN`, create the pre-release.
- Agent deploys to the test stand (`docker compose` over ssh) only if that was explicitly requested; ask the human for host and path if not known.
- Agent never: tags a stable version, moves `:latest`, deploys to production.
- Finish with a report: PR link, tag, image, test-stand check results.
