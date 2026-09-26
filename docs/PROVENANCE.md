# Guardian / Cerberus provenance record

This file records the source/provenance boundary used for the Phase 1A implementation prepared on 2026-09-24.

## Guardian / BrandBlocker lineage

Guardian is a hard fork and substantial rewrite of BrandBlocker by Menacho, with subsequent BadWolfMC development. The Phase 1A review used the supplied legacy reference archive:

- archive: `BrandBlocker(4).zip`
- SHA-256: `624184728aa379f6f9c895679e41df6dc260051fc8615c9c146ce09d579b8907`
- declared project version: `1.9.4-26.2`
- legacy plugin authors in `plugin.yml`: Menacho, mercurialmusic
- license lineage: GPLv3

BrandBlocker is behavioral/provenance reference material. Guardian does not preserve its delayed `PlayerJoinEvent` architecture, substring-based brand matching, console-command kick path, ignored `enable` setting, or username-prefix trust model.

## Guardian Protection / eZProtector lineage

The Phase 1A/1B handoff review uses two BadWolfMC eZProtector reference archives:

- current Paper reference: `ezProtector(3).zip`
  - SHA-256: `4e8d2f04ef33384cc1abe5dc2adf68c6726c43cd02b0644c34807f105643f3df`
  - this is byte-identical by SHA-256 to the previously reviewed `ezProtector(2).zip`
  - declared Paper project version: `1.4.0-26.2`
  - repository referenced by the supplied README: `BadWolfMC/eZProtector`
  - license declared by the supplied README: GPLv3
- historical proxy reference: `ezProtector-with-old-velocity-module(2).zip`
  - SHA-256: `cfd01fab6d2ef7c14c2adb7eb74bcb8b988f0cd54a3e0b70f7a0c66fd67a4624`
  - contains the older Velocity and Waterfall modules in addition to the Paper module
  - historical behavior/reference only; the authoritative Guardian contract explicitly excludes carrying legacy proxy Protection techniques into Phase 1B

Neither supplied archive contains Git metadata, so an exact BadWolfMC source commit SHA cannot be established from the archives themselves and is deliberately **not invented here**. Phase 1A incorporates no eZProtector command-protection implementation code. Phase 1B may use the identified GPLv3 BadWolfMC lineage for behavioral/provenance reference while implementing the approved Guardian Protection scope against supported current APIs.

Later AGPL eZProtector continuation code remains outside Guardian's provenance boundary unless licensing is explicitly reconsidered first.

## Phase 1A source archive

The implementation patch is based on the exact supplied Guardian archive:

- archive: `Guardian(20260924-084631).zip`
- SHA-256: `2285162a29a76007ead834c4cca9c2baa7e1e5b1834cd2ec27457eb92f262aea`

The final Phase 1A patch is verified with `git apply --check` against a fresh extraction of this archive before delivery.

## Phase 1A closeout baseline

The repository accepted for Phase 1A closeout and Phase 1B handoff is:

- archive: `Guardian(20260925-012627).zip`
- SHA-256: `5b128bfab97e1fd05a03132132b55c6ee694f1377a4c7bab1b7652a95b72180e`
- project version: `0.1.0-phase1a`
- archived automated results: 49 tests, 0 failures/errors/skips
- operator verification: startup recovery, schema fail-closed behavior, independent Admission/Protection activation, standalone Admission regression, and focused Velocity/Geyser/Floodgate regression all PASS

This is the authoritative implementation baseline for Phase 1B unless a later supplied repository explicitly supersedes it.

## Phase 1B implementation source supplied 2026-09-24

This Phase 1B implementation pass is based on the exact repository archive supplied for the pass:

- archive: `Guardian(20260925-014418).zip`
- SHA-256: `08db5a91d42b59a9d7605d670458a10c13904c4052ab82fb84b81cbb7eab5b15`
- starting project version: `0.1.0-phase1a`
- repository state represented by the archive: Phase 1A complete/live-verified per the included handoff and verification records

The exact eZProtector references supplied to this pass are:

- `ezProtector(4).zip` — SHA-256 `4e8d2f04ef33384cc1abe5dc2adf68c6726c43cd02b0644c34807f105643f3df`; byte-identical to the previously recorded current Paper reference hash
- `ezProtector-with-old-velocity-module(3).zip` — SHA-256 `cfd01fab6d2ef7c14c2adb7eb74bcb8b988f0cd54a3e0b70f7a0c66fd67a4624`; byte-identical to the previously recorded historical proxy reference hash

Phase 1B uses those archives for behavior/provenance review only. The Guardian Protection implementation is written against Guardian's domain boundaries and supported Paper APIs; no later AGPL continuation source is consulted or incorporated.


## Phase 1B closeout baseline — 2026-09-26

The repository accepted for Phase 1B closeout and Phase 2 handoff is:

- archive: `Guardian.zip`
- SHA-256: `caf98f98f1601939222b48ce497b6f0139755f1bec5b048f70d654299c6586e2`
- project version: `0.1.0-phase1b`
- repository test inventory: 73 tests
- operator-reported automated result: clean Java 25 / Gradle 9.7.1 gate PASS; all 73 tests PASS
- operator live result: full Phase 1B verification matrix PASS from a blank-slate Guardian installation

The closeout archive does not retain the terminal build log; the automated result above is therefore recorded as operator-confirmed rather than independently reconstructed from an archived report. The source tree and live verification establish Phase 1B as complete for project handoff.

This is the authoritative implementation baseline for Phase 2 unless a later supplied repository explicitly supersedes it.

## Phase 2 implementation candidate — 2026-09-26

Phase 2 replaces the Phase 0 synthetic Cerberus manifest and response validator with Guardian protocol v1 and a Fabric Loader-backed canonical manifest. Enumeration uses supported Fabric Loader APIs (`FabricLoader.getAllMods`, `ModContainer.getContainingMod`, origin metadata) and deliberately serializes no filesystem paths. The standalone Paper hybrid and Velocity-authoritative transport boundaries are retained.

BRIDGE-001 and BRIDGE-002 are retired in source by this candidate. Final Phase 2 closeout still requires the Java 25 / Gradle 9.7.1 clean gate plus live standalone Paper and Velocity-authoritative verification with representative real Fabric manifests.

## Phase 2 closeout hardening source — 2026-09-26

The final Phase 2 hardening/closeout pass is based on the exact repository archive supplied after successful live protocol-v1 testing:

- archive: `Guardian(4).zip`
- SHA-256: `3be945537ba459d6f1169305ac87760317d6e5704195f607ee4a129d3212430d`
- starting project version: `0.1.0-phase1b`
- supplied archived automated result: 73 tests, 0 failures/errors/skips
- operator result before this closeout patch: Java 25 / Gradle 9.7.1 build PASS
- operator live result: standalone Paper and Velocity-authoritative protocol-v1 matrices PASS with a representative 166-entry real Fabric manifest

The closeout hardening patch changes the project version to `0.1.0-phase2`, tightens Fabric mod-ID and unsigned-16-bit protocol-version representation bounds, expands adversarial tests to 85 total, and records the Phase 2 verification/Phase 3 handoff state. The final Java 25 clean gate for this exact patched source remains the operator confirmation required to promote the candidate to the Phase 2 closeout baseline.
