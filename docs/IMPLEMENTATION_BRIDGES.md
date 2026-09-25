# Implementation bridge register

This register tracks code that is intentionally present because it bridges a proven earlier-phase implementation into the current architecture, but is **not** the final implementation intended for public release. It exists so temporary scaffolding cannot silently become permanent.

The authoritative project plan remains the source of truth for scope and architecture. This file is the operational companion for concrete implementation debt that already exists in source.

## Maintenance rule

Add or update an entry whenever a change introduces or discovers any of the following:

- feasibility/prototype code retained across a phase boundary;
- temporary configuration or secret provisioning;
- deliberately simplified policy or validation logic standing in for a later subsystem;
- hard-coded operational values that a later phase is expected to own;
- supported-API integration implemented early but not yet productionized under its scheduled phase; or
- a temporary compatibility/testing hook that must not be mistaken for public behavior.

Every active bridge must identify the source locations, the reason it exists, the phase that owns its replacement/review, and an objective retirement condition. Closing a bridge means either removing/replacing it or explicitly promoting it into the authoritative contract as final behavior. Do not simply delete entries because the code has become familiar.

## Active bridges

### BRIDGE-001 — Phase 0 response validator and test-manifest evaluator

**Source:**

- `guardian-core/.../Phase0ResponseValidator.java`
- `guardian-core/.../Phase0ManifestEvaluator.java`
- `guardian-protocol/.../GuardianProtocol.java` (`PHASE0_DENY_MOD_ID`)
- Paper and Velocity Admission adapters that currently invoke `Phase0ResponseValidator`

**Why it exists:** Phase 0 needed deterministic `CERBERUS_VERIFIED`, `MANIFEST_DENIED`, protocol-mismatch, nonce-mismatch, malformed-response, and timeout outcomes before the real manifest/policy engine existed. Phase 1A deliberately retained that proven transport boundary rather than replacing working networking with speculative later-phase code.

**Owner:** Phase 2 for the stable protocol/manifest validation path; Phase 3 for real manifest policy evaluation.

**Retirement condition:** Production Admission no longer depends on `Phase0ResponseValidator`, `Phase0ManifestEvaluator`, or the synthetic `guardian-phase0a-deny` manifest entry. Equivalent failure distinctions remain covered by production tests.

### BRIDGE-002 — Cerberus Phase 0 manifest and JVM diagnostic switches

**Source:**

- `cerberus-fabric/.../CerberusClient.java`
- `cerberus-fabric/src/main/resources/fabric.mod.json`
- `cerberus.phase0a.protocol`
- `cerberus.phase0a.deny`
- `cerberus.phase0a.suppressResponse`
- `cerberus.phase0a.malformed`

**Why it exists:** The current client intentionally reports only the small feasibility manifest and exposes test switches used to prove distinct admission outcomes. It is not yet the final Cerberus inventory implementation.

**Owner:** Phase 2.

**Retirement condition:** Cerberus enumerates the contract-defined Fabric Loader manifest, reports parent/contained relationships and required metadata, follows stable protocol/capability negotiation, and any retained diagnostics are renamed/documented as deliberate development tooling rather than `phase0a` behavior. The Fabric metadata no longer describes the client as a Phase 0A feasibility spike.

### BRIDGE-003 — Phase 0B proxy assertion secret provisioning

**Source:**

- `GUARDIAN_PHASE0B_PROXY_SECRET` use in Guardian-Velocity and Guardian-Paper
- current HMAC proxy-admission assertion bootstrap

**Why it exists:** Phase 0B proved authenticated Velocity → Paper admission assertions using a server-controlled shared secret. The cryptographic trust boundary is valid, but the environment-variable name and provisioning UX are feasibility-era scaffolding.

**Owner:** Phase 5, with Phase 7 documentation/release UX follow-through.

**Retirement condition:** Proxy assertion secret/key provisioning, validation, rotation expectations, diagnostics, and deployment documentation use production Guardian configuration/naming. No production path depends on the `PHASE0B` environment-variable contract unless the authoritative plan explicitly promotes it.

### BRIDGE-004 — Guardian-Velocity retained feasibility adapter

**Source:**

- `guardian-velocity/.../GuardianVelocityPlugin.java`
- Phase 0B-labelled logs/Javadocs and fixed `HANDSHAKE_TIMEOUT_SECONDS`

**Why it exists:** The Velocity CONFIGURATION lifecycle, channel-consumption boundary, session reuse, Bedrock classification, and proxy assertion flow were live-proven in Phase 0B and intentionally retained during Phase 1A. The adapter has not yet received its full production configuration/diagnostics pass.

**Owner:** Phase 5.

**Retirement condition:** Velocity authority has production configuration ownership, configurable operational timing where appropriate, final diagnostics/naming, finalized assertion provisioning, and tests for the Phase 5 acceptance matrix. Phase 0B wording is removed from production logs/Javadocs.

### BRIDGE-005 — Early Geyser/Floodgate integration behavior

**Source:**

- Guardian-Velocity `BedrockDetector` integration and disagreement handling
- Guardian-Paper backend Floodgate sanity-check path

**Why it exists:** Phase 0B live testing proved the supported Geyser/Floodgate APIs and established the correct invariant that Bedrock classification precedes Cerberus. That working implementation arrived before the roadmap's dedicated production integration phase. In particular, current proxy disagreement handling logs the inconsistency and treats any positive supported API signal as Bedrock for the feasibility path.

**Owner:** Phase 4.

**Retirement condition:** Capability discovery, mismatch semantics, configurable Bedrock policy, diagnostics, and backend sanity checks have explicit production tests/documentation and no remaining “feasibility spike” behavior or wording.

## Resolved bridges

Resolved entries remain here as provenance for decisions that changed during implementation. They are no longer counted as active implementation debt.

### BRIDGE-006 — Initial-startup invalid-file recovery

**Resolution:** Phase 1A live testing showed that strict startup refusal can leave Guardian absent if an administrator misses the startup error. The authoritative contract now defines explicit recovery instead: malformed/structurally invalid initial config and locale files are preserved to UTC timestamped `.bak` files before safe packaged defaults/fallback behavior is used and fully revalidated. Unsupported schema versions are deliberately excluded from automatic recovery. Reload remains non-mutating and retains the prior valid snapshot.

**Resolved in:** Phase 1A closeout, 2026-09-24.

**Regression ownership:** Phase 7 should retain this behavior in release/upgrade tests, but there is no remaining implementation bridge.

## Phase 1A closeout state

As of 2026-09-24, **Phase 1A is complete**.

- **Clean Java 25 Gradle build/tests:** PASS — supplied closeout repository contains 49 tests with 0 failures/errors/skips; operator reports the clean build remains green.
- **Patch/application integrity:** PASS.
- **Source hygiene:** PASS; the previously reported unused import was ordinary cleanup rather than an implementation bridge.
- **Local Paper/configuration safety:** PASS — first-start generation, malformed config backup/recovery, invalid fallback-locale backup/recovery, and unsupported-schema fail-closed behavior were operator-confirmed.
- **Independent domain activation:** PASS — Admission/Protection on/off combinations were exercised.
- **Focused standalone Admission regression:** PASS.
- **Focused Velocity/Geyser/Floodgate regression:** PASS.

The active bridge register was reviewed at closeout. BRIDGE-001 through BRIDGE-005 remain intentionally active and retain their existing later-phase owners and retirement conditions. No Phase 1A completion result promotes those bridges into final public behavior.

Phase 1B may proceed from this baseline. Any temporary Protection implementation introduced during Phase 1B must be added to this register with an owner and retirement condition rather than left as an implicit TODO.


## Phase 1B bridge review

The Phase 1B Guardian Protection implementation introduces **no new temporary implementation bridge**. The player-command execution, command-tree visibility, downstream-suggestion suppression, bypass-resolution, notification, configuration, and tree-refresh paths are intended production architecture for the Phase 1B scope.

The package-private Guardian-Paper atomic reload/reconciliation method is a lifecycle seam for the later supported administrative surface, not a raw/replacement reload mechanism and not a temporary compatibility path. The public `/guardian reload` command itself remains deliberately roadmap-owned by the operations phase. BRIDGE-001 through BRIDGE-005 remain unchanged and are outside Phase 1B ownership.
