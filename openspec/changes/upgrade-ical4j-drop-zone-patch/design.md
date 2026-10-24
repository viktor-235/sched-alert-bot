# Design

## Context

The workaround is described in the archived ics-export change (`openspec/changes/archive/2026-10-24-ics-export/design.md`, section 3a). In short, `ZoneRulesBuilder.java` is a copy of the 4.4.0 class with one line changed, and it shadows the class in the jar because application classes come first on the classpath. `ZoneRulesBuilderPatchTest` checks three things: the copy is loaded, Moscow is at +03:00 in 2026, and the ical4j version is exactly 4.4.0. The version check makes any upgrade fail until someone decides what to do with the copy.

Upstream PR #955 fixes the bug more broadly than our one-line change. The "all RRULEs expired" guard now looks only at observances that would produce a transition rule. According to the maintainer, the results on the bundled zones are identical to our fix: 64 zones corrected, 5 still diverge (Cairo, Casablanca, El_Aaiun, Gaza, Hebron, all with Ramadan-dependent DST).

## Goals / Non-Goals

**Goals:**
- Run on an unpatched ical4j release with the same `.ics` output for configured zones.
- Keep a regression test for #903 that does not depend on the patch.

**Non-Goals:**
- The 5 Ramadan-DST zones. They diverge with and without the patch, and no configured site uses them.
- Other ical4j features or API changes in the new release beyond what is needed to compile and keep the output.

## Decisions

### 1. Target release: the first release containing PR #955, not `develop` or a snapshot

Check that the fix is in the release, don't rely on the changelog: the release tag's `ZoneRulesBuilder.buildTransitionRules` must contain the PR #955 change, or the release notes must list #955 or #903. A snapshot or a JitPack build of `develop` is rejected: the patch already works, so there is no reason to swap a reviewed one-line copy for an unreleased artifact.

If the next release does not contain the fix, this change stays blocked. The existing upgrade path in the javadoc still applies to unrelated upgrades: re-apply the patch to the new source and bump the version in `ZoneRulesBuilderPatchTest`.

### 2. Delete the copy, keep behavior guarded at the output level

Delete both the copy and `ZoneRulesBuilderPatchTest`. Its "copy is loaded" and "version is 4.4.0" checks become meaningless, and its offset check belongs to ical4j. Our contract is the written `.ics`. The existing `IcsWriterTest` case that expects `DTSTART;TZID=Europe/Moscow:20261025T150000` for `2026-10-25T12:00:00Z` is that guard: it fails with an unfixed ical4j (`...T160000`). Before deleting the copy, run that test once against the new jar with the copy removed, so we know it passes because of the upstream fix.

Alternative: keep a ical4j-level offset test (`TimeZoneRegistry.getGlobalZoneId("Europe/Moscow")` at +03:00 in July 2026) under our own package. Rejected: it tests the library, not our output, and the `IcsWriterTest` case already catches the same regression.

### 3. Golden file: regenerate only the `VTIMEZONE` block

`events-moscow.ics` embeds the tzurl.org definition bundled in the jar. A new release may ship updated definitions. If the golden test fails, the diff must be confined to the `VTIMEZONE` block. Any change in `DTSTART`/`DTEND`/`DTSTAMP` lines or in property order is a behavior change and blocks the upgrade until it is understood.

### 4. Dependency hygiene

Remove `compileOnly jspecify`: only the copy imports it. Re-check that the runtime classpath still excludes groovy, caffeine and jparsec (ics-export task 2.1), since a new release may change its transitive dependencies or optional markers.

## Risks / Trade-offs

- [Upstream fix differs from ours in some zone we don't test] → The maintainer reports results identical to our fix on all 339 bundled zones. We only configure Europe/Moscow, which `IcsWriterTest` covers. The Amsterdam DST test covers a zone that still has DST.
- [New release changes output formatting or the default `PRODID`/property order] → Golden-file tests catch it; see decision 3.
- [Release takes long] → Nothing breaks meanwhile. The patch keeps working, and the version check in `ZoneRulesBuilderPatchTest` stops an accidental upgrade without a decision.

## Migration Plan

Ship as a PATCH release. On deploy, the next run rewrites the calendar files, and only the `VTIMEZONE` block can change (see decision 3). Rollback: redeploy the previous image; no data is involved.
