# Phase 2 handoff — Cerberus and protocol v1

Phase 1B closed successfully on 2026-09-26. This document is the concise implementation handoff for Phase 2; `docs/Guardian_Cerberus_Authoritative_Project_Plan.md` remains controlling if this summary and the contract differ.

## Authoritative baseline

- closeout archive: `Guardian.zip`
- SHA-256: `caf98f98f1601939222b48ce497b6f0139755f1bec5b048f70d654299c6586e2`
- project version: `0.1.0-phase1b`
- repository test inventory: 73 tests
- operator-reported automated result: clean Java 25 / Gradle 9.7.1 gate PASS; all 73 tests PASS
- live result: Phase 1B verification matrix PASS from a blank-slate Guardian installation

Do not reconstruct earlier phases from chat history. Treat the repository, the authoritative plan, this handoff, `docs/IMPLEMENTATION_BRIDGES.md`, and `docs/PROVENANCE.md` as source of truth.

## Phase 2 goal

Build the real Fabric Cerberus client manifest and stable initial Guardian protocol while preserving the live-proven Phase 0 transport architecture.

Required scope:

- enumerate Loader-known mods through supported Fabric Loader APIs;
- define one canonical manifest representation and deterministic serialization;
- include relevant parent/contained relationships rather than silently hiding nested entries;
- include bounded Minecraft, Fabric Loader, Cerberus, protocol-version, and capability metadata;
- replace the synthetic Phase 0 manifest with the real canonical manifest;
- bind every attestation to a fresh nonce/session and reject stale, reused, mismatched, malformed, duplicate, or oversized responses;
- define protocol-v1 payload, mod-count, ID/version-string, nesting, hash-field, retry, and outstanding-handshake limits before production activation;
- implement explicit version/capability negotiation so recognizable incompatibility produces `CERBERUS_PROTOCOL_UNSUPPORTED`, not a generic timeout;
- preserve distinct `CERBERUS_REQUIRED`, `CERBERUS_TIMEOUT`, `CERBERUS_PROTOCOL_UNSUPPORTED`, `MANIFEST_INVALID`, and successful verified outcomes;
- retain privacy minimization: no absolute paths, OS usernames, launch arguments, hardware identifiers, unrelated telemetry, IP data, or arbitrary files;
- define deterministic behavior for directory/classpath/development-style mod origins and document observed real-manifest baseline/bootstrap/runtime entries for Phase 3;
- keep protocol version independent from Guardian/Cerberus release versions; and
- add meaningful protocol/manifest boundary, malformed-input, canonicalization, and transport regression tests.

## Settled transport boundaries

Standalone Paper remains hybrid on 26.2: CONFIGURATION performs client classification plus Cerberus presence/protocol gating, while compatible Cerberus enters bounded PLAY quarantine for Guardian → Cerberus challenge and Cerberus → Guardian response. Do not try to force the entire standalone exchange back into CONFIGURATION through unsupported APIs.

Guardian-Velocity remains the preferred BadWolfMC network authority and completes Cerberus admission during Velocity's awaited CONFIGURATION lifecycle. Security-sensitive client protocol channels remain consumed at the proxy rather than blindly forwarded to Paper. Guardian-Paper in Velocity authority mode verifies the trusted proxy result rather than re-attesting the same connection.

Bedrock classification remains prior to Cerberus applicability. Positively identified Bedrock must never receive a Cerberus challenge.

## Phase boundaries

Do not implement the Phase 3 policy engine during Phase 2. In particular, defer named profiles, full mod allowlist/denylist policy, required-mod rules, per-mod allow/deny decisions, version predicates as administrator policy, LuckPerms profile resolution, and admission policy bypasses. Phase 2 may characterize manifest categories and expose clean validated data structures needed by Phase 3, but should not invent policy semantics prematurely.

Do not implement signed official Cerberus artifact identity in Phase 2; that remains a Phase 6 exploration. No secret embedded in Cerberus may be treated as proof that a hostile client is honest.

Guardian Protection is complete for Phase 1B. Keep it regression-only unless Phase 2 reveals a concrete shared-host defect.

## Implementation bridges owned by Phase 2

Phase 2 owns retirement of:

- **BRIDGE-001** — `Phase0ResponseValidator`, `Phase0ManifestEvaluator`, the synthetic deny-mod entry, and adapters that depend on the Phase 0 response validator;
- **BRIDGE-002** — the tiny feasibility manifest plus `cerberus.phase0a.*` test switches/naming and Phase 0A Fabric metadata.

Retire these only when production protocol-v1 equivalents provide the same distinct failure semantics and are covered by tests. Diagnostic hooks may survive only if deliberately renamed and documented as development tooling rather than feasibility behavior.

BRIDGE-003 through BRIDGE-005 retain their later owners and should not be opportunistically rewritten during Phase 2.

## Engineering constraints

- Java 25; Minecraft/Paper/Fabric target 26.2.
- Use only supported Paper, Velocity, Fabric, Java, and public Minecraft-facing abstractions.
- No NMS, CraftBukkit internals, implementation reflection, server Mixins, packet libraries, or speculative 26.3 compatibility.
- Preserve existing startup recovery and atomic configuration activation semantics.
- Keep `guardian-core`, `guardian-protocol`, and Cerberus responsibilities cleanly separated from platform adapters.
- Keep all player/staff-facing Guardian strings in locale resources.
- Update `docs/IMPLEMENTATION_BRIDGES.md` if Phase 2 introduces any temporary scaffolding.
- Prefer small reviewable patches against the exact supplied repository and verify them with `git apply --check` against a fresh extraction.

## Completion expectation

Before Phase 2 closes, run the full Java 25 clean build/test gate, exercise both the standalone Paper hybrid and Velocity-authoritative Fabric/Cerberus paths with real manifests, verify the distinct failure reasons, characterize representative real Fabric manifests (including nested and development-style origins where practical), review privacy/log output, and confirm BRIDGE-001/002 are retired or explicitly accounted for.
