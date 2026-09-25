# Phase 1B handoff — Guardian Protection / eZProtector successor

Phase 1A closed successfully on 2026-09-24. This document is a concise implementation handoff; the authoritative project plan remains controlling if this summary and the contract ever differ.

## Authoritative implementation baseline

- Guardian archive: `Guardian(20260925-012627).zip`
- SHA-256: `5b128bfab97e1fd05a03132132b55c6ee694f1377a4c7bab1b7652a95b72180e`
- project version: `0.1.0-phase1a`
- automated closeout state: 49 tests, 0 failures/errors/skips
- live closeout state: all Phase 1A completion gates PASS

Do not reconstruct Phase 1A behavior from chat history. Treat the repository and `docs/Guardian_Cerberus_Authoritative_Project_Plan.md` as authoritative.

## Protection provenance references

Current BadWolfMC Paper reference:

- `ezProtector(3).zip`
- SHA-256: `4e8d2f04ef33384cc1abe5dc2adf68c6726c43cd02b0644c34807f105643f3df`
- byte-identical by SHA-256 to the previously reviewed `ezProtector(2).zip`
- GPLv3 BadWolfMC lineage
- behavioral/provenance reference; old architecture does not override Guardian

Historical proxy reference:

- `ezProtector-with-old-velocity-module(2).zip`
- SHA-256: `cfd01fab6d2ef7c14c2adb7eb74bcb8b988f0cd54a3e0b70f7a0c66fd67a4624`
- contains the former Velocity and Waterfall modules
- historical intent/reference only
- legacy proxy Protection techniques MUST NOT be transplanted into Phase 1B

The supplied archives contain no Git metadata, so no source commit SHA is asserted. Phase 1B should prefer a clean Guardian implementation of the approved behavior; if any source is directly incorporated from the GPLv3 BadWolfMC lineage, preserve the applicable notices and record the exact archive/hash (and commit SHA if later available).

## Phase 1B required scope

Implement only the contract-approved Guardian Protection behavior:

- platform-neutral Protection decision/reason/rule models in `guardian-protection`;
- command execution policy for configured command roots;
- command visibility policy with explicit allowlist/denylist modes;
- namespaced-command policy with explicit allowlist/denylist modes;
- deterministic root-command normalization;
- root-command visibility filtering through supported Paper command-tree APIs/events;
- downstream argument-suggestion suppression whenever a root is hidden;
- centralized bypass resolution used consistently by Protection surfaces;
- global/feature-scoped bypass concepts and optional per-command visibility bypass;
- permission-gated staff notifications independent from bypass permissions;
- locale-backed denial/staff messages;
- logging/diagnostics;
- supported Paper command-tree refresh after visibility configuration changes;
- automated tests required by the authoritative Phase 1B acceptance matrix;
- eZProtector → Guardian configuration concept mapping;
- eZProtector → Guardian permission migration mapping using only new `guardian.*` nodes.

Guardian Protection is initially Paper-authoritative. Do not add proxy-side Protection merely because historical eZProtector had Velocity/Waterfall modules.

## Explicit exclusions

Do not port:

- eZProtector client-brand/mod enforcement;
- 5zig, BetterSprinting, Schematica, WorldDownloader, BetterPvP, or VoxelMap countermeasures;
- fake plugin-list or fake version behavior;
- legacy Waterfall/Velocity command-protection techniques;
- `ezprotector.*` permission compatibility aliases;
- raw legacy config reload behavior.

BadWolfMC does not require punishment commands for the initial Phase 1B migration.

## Phase 1A boundaries that must remain intact

- Admission and Protection remain independently enableable.
- `guardian-protection` remains platform-neutral and independent from `guardian-core`.
- Guardian Protection does not require Velocity, Cerberus, Geyser/Floodgate, or a successful Admission attestation.
- Admission behavior must not become dependent on command-protection state.
- All player/staff-facing Guardian strings come from locale resources.
- Configuration continues to use parse → validate → immutable candidate → atomic activation.
- Bad reloads leave the prior active snapshot in place and do not mutate administrator edits.
- Recoverable malformed initial files follow the Phase 1A timestamped-backup/default-recovery contract.
- No NMS, implementation reflection, packet library, or unsupported internals.

## Existing implementation bridges

Review `docs/IMPLEMENTATION_BRIDGES.md` before and after Phase 1B. BRIDGE-001 through BRIDGE-005 remain active but are not owned by Phase 1B. Do not opportunistically rewrite them unless Phase 1B reveals a concrete architectural conflict.

Any temporary Phase 1B scaffolding must receive its own bridge entry with an owning phase and objective retirement condition.

## First Phase 1B design task

Before writing listeners, inspect the current `guardian-protection` boundary, Guardian-Paper configuration/runtime composition, locale system, and both eZProtector references. Then define the smallest coherent Protection configuration/domain model that satisfies the authoritative Phase 1B contract without preserving legacy schema or permission names.

Verify the current supported Paper 26.2 command execution, command-tree visibility, suggestion, and command-tree refresh APIs before relying on version-sensitive behavior.

## Implementation-pass baseline supersession

The Phase 1B coding pass on 2026-09-24 was explicitly supplied a later Guardian archive and therefore uses it as the authoritative source baseline under this handoff's supersession rule:

- `Guardian(20260925-014418).zip`
- SHA-256 `08db5a91d42b59a9d7605d670458a10c13904c4052ab82fb84b81cbb7eab5b15`

The supplied eZProtector archives have the same hashes as the Paper and historical-proxy references already recorded above, despite their incremented archive filenames (`ezProtector(4).zip` and `ezProtector-with-old-velocity-module(3).zip`).
