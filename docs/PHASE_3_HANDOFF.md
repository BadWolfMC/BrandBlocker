# Phase 3 handoff — Guardian policy engine

This handoff is revised from the Phase 2.5 artifact-identity source on 2026-09-26. Use it after the Phase 2.5 clean Java 25 / Gradle 9.7.1 gate in `PHASE_2_5_VERIFICATION.md` passes. `docs/Guardian_Cerberus_Authoritative_Project_Plan.md` remains controlling if this summary and the contract differ.

## Authoritative baseline

- project version entering Phase 3: `0.1.0-phase2.5` after the Phase 2.5 closeout gate
- protocol: Guardian/Cerberus protocol v1
- repository source test inventory after Phase 2.5 implementation: 102 tests
- standalone transport: CONFIGURATION classification/presence gate + bounded PLAY challenge/response quarantine
- Velocity transport: awaited CONFIGURATION challenge/response, security-sensitive channel consumption at proxy, trusted admission assertion to Paper
- real manifest characterization: 166 Loader-known entries on the representative BadWolfMC Fabric client
- Phase 2 bridges retired: BRIDGE-001 and BRIDGE-002
- bridges intentionally still active: BRIDGE-003, BRIDGE-004, BRIDGE-005

Do not reconstruct earlier phases from chat history. Treat the repository, the authoritative plan, this handoff, `PHASE_2_5_IMPLEMENTATION.md`, `PHASE_2_5_VERIFICATION.md`, `PHASE_2_IMPLEMENTATION.md`, `PHASE_2_VERIFICATION.md`, `IMPLEMENTATION_BRIDGES.md`, and `PROVENANCE.md` as source of truth.

## Phase 2.5 inputs Phase 3 must consume

Do not redesign these inputs while implementing policy:

- protocol v1 requires `CAP_ARTIFACT_SHA256`;
- every top-level `ARCHIVE` manifest entry has an exact SHA-256 of the installed outer JAR;
- `NESTED`, `BUILTIN`, `DIRECTORY`, and `MIXED_OR_UNKNOWN` entries are explicitly unhashed under the Phase 2.5 origin contract;
- nested entries retain their existing immediate parent relationship, so the top-level outer hash commits to contained bytes without forcing administrators to catalogue every nested library separately;
- `plugins/Guardian/artifacts.yml` is the durable, validated, deterministic exact-artifact catalog produced/merged by `/guardian artifacts scan` from temporary administrator input in `approved-artifacts/`;
- the catalog supports multiple versions per mod ID and multiple hashes per ID/version, and scanning is add-only rather than presence-synchronized; and
- catalog membership is identity data only. Phase 3 must explicitly decide what policy meaning, if any, a catalogued or uncatalogued artifact has.

The trust limitation is non-negotiable: SHA-256 verifies exact bytes **reported by a cooperating Cerberus client**; it does not prove a hostile/replaced Cerberus client reported truthfully.

## Phase 3 goal

Turn normalized client classifications and structurally valid canonical Cerberus manifests into flexible, deterministic admission-policy decisions without changing the proven transport architecture.

Implement the authoritative Phase 3 scope:

- default admission policy;
- named profiles with explicit deterministic priority;
- canonical per-client-class `ALLOW`, `DENY`, and `REQUIRE_CERBERUS` actions;
- optional normalized allowlist/denylist brand rules only for otherwise unknown Java brands;
- mod-policy `ALLOWLIST` / `DENYLIST` semantics, or an equally explicit unlisted default action;
- orthogonal required-mod rules;
- explicit per-mod allow/deny rules;
- deterministic unknown/unlisted-mod handling;
- administrator version rules/predicates;
- contained/nested-mod policy semantics;
- explicit baseline/bootstrap/runtime manifest-entry treatment derived from the Phase 2 evidence;
- explicit exact-artifact policy semantics over the Phase 2.5 SHA-256 field and durable catalog; version-only rules may remain available where policy deliberately chooses them, but hashes must never be described as remote attestation;
- classification-specific actions and explicit decision reasons;
- validation that rejects contradictory/ambiguous policy instead of relying on hidden precedence;
- policy-scoped admission bypass permissions that never bypass protocol/session/manifest integrity;
- atomic parse → validate → immutable snapshot activation; and
- files-only validation.

Implement profile resolution in the authoritative order:

1. explicit configured identity/UUID override;
2. highest-priority matching named profile from a supported pre-login-capable provider path;
3. default Guardian profile.

LuckPerms is the first-class optional provider named by the project plan. `guardian.admission.profile.<profile-id>` is the stable profile-selection permission shape. Multiple matching profiles must resolve deterministically by explicit priority; provider iteration order must never decide policy.

## Manifest evidence Phase 3 must account for

Do not equate “every Loader-known manifest entry” with “every administrator-selected client mod.” Phase 2 observed all of the following in one real 166-entry client:

- built-in `minecraft` and `java`;
- top-level `fabricloader`;
- top-level `fabric-api` plus many nested Fabric API modules;
- ordinary top-level client mods;
- large bundled systems such as C2ME with many nested implementation modules/libraries;
- nested language/runtime libraries; and
- multi-level containment.

Phase 3 must therefore define a deterministic **policy-addressable entry** model. Administrators should not need to enumerate baseline/bootstrap/runtime and bundle-internal entries merely to make allowlist mode usable, but nested entries must not disappear into an invisible bypass. The exact rule belongs in Phase 3 and must be documented and tested from this evidence rather than guessed from mod filenames.

`OriginKind.DIRECTORY` and `MIXED_OR_UNKNOWN` are structurally valid Phase 2 values. Phase 3 may define explicit policy behavior for development-style origins, but it must not expose paths or infer artifact identity from a directory origin.

## Security and precedence invariants

Preserve the authoritative evaluation order:

```text
trusted origin classification
        ↓
normalized Java client classification
        ↓
resolve admission profile
        ↓
evaluate client-class action
        ├── DENY -> applicable client-policy bypass may exempt
        ├── ALLOW -> no Cerberus interrogation required
        └── REQUIRE_CERBERUS
                 ↓
        complete + structurally validate protocol v1
                 ↓
        evaluate mod policy
                 ↓
        apply applicable mod-policy bypasses
                 ↓
        ALLOW or MANIFEST_DENIED
```

Raw brand rules remain subordinate input for `JAVA_UNKNOWN` only. They cannot override trusted Bedrock origin, turn positively classified Fabric into ordinary `ALLOW`, or reverse protocol/integrity failures.

Admission bypasses are policy-scoped only:

- client bypasses may exempt configured client-class `DENY` outcomes;
- client bypasses must not remove `REQUIRE_CERBERUS`;
- mod bypasses apply only after a compatible, structurally valid Cerberus exchange;
- no bypass may exempt nonce mismatch, protocol incompatibility, malformed/canonicalization failure, payload limits, duplicate/replay/session failures, or proxy-assertion authentication.

## Configuration-validation requirements

Reject rather than silently resolve contradictions such as:

- a mod simultaneously required and unconditionally denied;
- duplicate rule IDs;
- invalid version expressions;
- ambiguous profile-priority ties unless a separately documented deterministic tie rule is deliberately chosen;
- incompatible rule combinations; and
- permission/profile identifiers that violate the project's bounded normalized identifier rules.

A required mod should count as permitted for membership purposes so administrators do not have to duplicate it in both `required` and `allowed` merely to say “this must be present.”

## Transport and later-phase boundaries

Do not redesign the Phase 2/2.5 wire protocol or artifact catalog merely to implement policy. `guardian-core` should consume the validated platform-neutral manifest and produce deterministic policy decisions; Paper/Velocity adapters should remain transport/lifecycle hosts.

Do not opportunistically pull forward:

- Phase 4 production Geyser/Floodgate work (BRIDGE-005);
- Phase 5 Velocity production configuration, final diagnostics, or proxy-secret provisioning (BRIDGE-003/004);
- Phase 6 signed official Cerberus identity or hostile-client attestation claims;
- operations/admin command surfaces from later phases unless a minimal files-only validation seam is explicitly required by Phase 3.

Guardian Protection remains regression-only unless Phase 3 exposes a concrete shared configuration/runtime defect.

## Required Phase 3 acceptance coverage

At minimum, automated tests must cover:

- client-class `ALLOW`, `DENY`, and `REQUIRE_CERBERUS`;
- an allowlisted unusual `JAVA_UNKNOWN` brand and a denied unusual brand;
- Fabric remaining attestation-required regardless of unknown-brand rules;
- mod `ALLOWLIST` and `DENYLIST` semantics;
- unlisted mods in both modes;
- required mods in both modes;
- required mod with version constraint;
- explicit per-mod allow/deny behavior;
- nested/contained-entry semantics and baseline-entry treatment;
- invalid/contradictory configuration;
- deterministic profile priority and default fallback;
- optional LuckPerms provider behavior when available and honest fallback when absent;
- client/mod policy bypasses; and
- proof that policy bypasses cannot bypass protocol/session/manifest integrity.

Before closing Phase 3, re-run focused standalone and Velocity admission regressions, but do not repeat the entire Phase 2 transport abuse matrix unless policy integration changes those paths.
