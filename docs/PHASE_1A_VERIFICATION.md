# Phase 1A verification plan

Phase 1A establishes Guardian's production foundation and BrandBlocker replacement without implementing Phase 1B command protection or the later full Cerberus/mod-policy engine.

## Current gate status (2026-09-24)

- Clean Java 25 Gradle build/tests: **PASS** (operator-reported after the Guardian-Velocity test-classpath fix).
- Phase 1A source/patch integrity checks: **PASS**.
- Local Paper/configuration-safety smoke tests: **PARTIAL PASS**. First-start generation, prior strict malformed/invalid-file behavior, and Protection foundation activation were operator-confirmed. The startup behavior was then intentionally changed to timestamped backup + safe default/fallback recovery and requires one final smoke check.
- Focused standalone Admission regression: **PASS** (operator-reported 2026-09-24).
- Focused Velocity-network regression: **PASS** (operator-reported 2026-09-24).

Active temporary code carried across phase boundaries is tracked in `IMPLEMENTATION_BRIDGES.md`. A green build alone does not close the remaining live verification gate.

## Supported-API sanity pass (2026-09-24)

The Phase 1A review re-checked the version-sensitive boundaries before retaining the Phase 0 transport:

- Paper 26.2 `PlayerConfigurationConnection` remains the supported CONFIGURATION-stage connection abstraction and exposes client brand, Adventure audience, listening plugin channels, and plugin-message sending.
- Paper 26.2 `PlayerConnectionValidateLoginEvent` remains the supported final login/configuration gate and explicitly supports `kickMessage(Component)`; its API note also says not to send packets through the connection from that validation event, which is why Guardian continues to use it only as a decision gate.
- Velocity's `PlayerConfigurationEvent` remains an awaited CONFIGURATION event; Velocity waits for it before continuing/ending configuration.
- Velocity's plugin-message guidance continues to require security-sensitive messages to be marked handled when they must not be forwarded, specifically warning that careless forwarding can let clients impersonate the proxy to backends.
- Fabric's 26.2 configuration networking API continues to expose a START point where packets may be sent and dedicated CONFIGURATION networking handlers.

References:

- https://jd.papermc.io/paper/26.2/io/papermc/paper/connection/PlayerConfigurationConnection.html
- https://jd.papermc.io/paper/26.2/io/papermc/paper/event/connection/PlayerConnectionValidateLoginEvent.html
- https://jd.papermc.io/velocity/4.0.0/com/velocitypowered/api/event/player/configuration/PlayerConfigurationEvent.html
- https://docs.papermc.io/velocity/dev/plugin-messaging/
- https://maven.fabricmc.net/docs/fabric-api-0.149.1%2B26.2/net/fabricmc/fabric/api/client/networking/v1/ClientConfigurationConnectionEvents.html

No Phase 1A change therefore requires NMS, implementation reflection, packet interception libraries, or unsupported Fabric internals.

## Automated build

From the repository root with Java 25:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

The test suite is expected to cover, at minimum:

- explicit `ALLOW` / `DENY` / `REQUIRE_CERBERUS` client actions;
- exact normalized unknown-brand allowlist/denylist semantics;
- Fabric classification remaining outside unknown-brand rule override;
- 128-character Guardian permission bounds;
- `guardian-core` remaining platform-neutral and independent from Protection;
- `guardian-protection` remaining platform-neutral and independent from Admission core;
- schema-v1 configuration validation;
- all four Admission/Protection enable-state combinations;
- the low-level runtime manager rejecting malformed initial config/locale input without mutating administrator files;
- startup recovery preserving invalid bytes to timestamped backups before restoring packaged defaults or locale fallback behavior;
- malformed/structurally invalid config reload preserving the prior runtime snapshot and file bytes;
- invalid locale reload preserving the prior runtime snapshot and file bytes;
- required fallback locale packaging;
- MiniMessage placeholder values being inserted as unparsed data;
- retained protocol/proxy assertion tests from Phase 0;
- retained Velocity Bedrock/session behavior tests.

## Local Paper smoke tests

Use a clean Paper 26.2 server and the built `guardian-paper` JAR.

1. Start with no `plugins/Guardian/` directory. Confirm Guardian writes `config.yml` and `locales/en_us.properties` and loads with Admission enabled / Protection disabled.
2. Stop the server, deliberately make `config.yml` malformed, restart, and confirm Guardian:
   - logs the validation failure prominently;
   - preserves the exact invalid file beside it as `config.yml.invalid-<UTC timestamp>.bak`;
   - restores the packaged `config.yml`; and
   - successfully activates only after reparsing/revalidating that restored default.
3. Repeat with a malformed/missing-required-key `locales/en_us.properties`; confirm the invalid locale is preserved as a timestamped `.bak`, the packaged fallback locale is restored, and Guardian activates. An unsupported `schema-version` must still fail without automatic replacement.
4. Exercise the four feature combinations across restarts:
   - Admission on / Protection off;
   - Admission off / Protection on;
   - both on;
   - both off.
   Protection-on in Phase 1A should only report that the domain foundation is active; it must install no eZProtector command filtering yet.
5. With standalone authority, regress the proven Phase 0A paths:
   - vanilla -> allow;
   - Fabric without Cerberus -> `CERBERUS_REQUIRED` before world entry;
   - Fabric + compatible Cerberus -> bounded PLAY quarantine -> allow after verified response;
   - incompatible Cerberus -> compatibility-specific denial;
   - timeout / malformed response / test manifest denial remain distinct.
6. Add one unusual Java brand to `admission.unknown-brands.brands` in `ALLOWLIST` mode and verify it is admitted as `JAVA_UNKNOWN` only when exactly matched after normalization.
7. Verify a near-match (for example `example-extra` when only `example` is listed) remains denied.
8. As a regression guard, adding `fabric` to unknown-brand rules must not turn a positively classified Fabric connection into ordinary `ALLOW`.

## Velocity-network regression

Phase 1A intentionally retains the live-proven Phase 0B transport. Before calling Phase 1A complete on BadWolfMC, re-run a focused Velocity + Paper regression:

- vanilla Java admission;
- Fabric + Cerberus admission;
- Fabric without Cerberus distinct denial;
- Bedrock via Geyser/Floodgate with no Cerberus challenge;
- authenticated Velocity -> Paper assertion;
- backend switch reuses the same proxy-session admission;
- full reconnect creates a fresh admission session;
- direct/backend assertion spoofing remains rejected/consumed according to the Phase 0 trust boundary.

The `GUARDIAN_PHASE0B_PROXY_SECRET` environment variable remains a deliberate Phase 0 provisioning artifact in this implementation candidate. Final proxy secret/configuration UX is Phase 5 and should not be treated as frozen by Phase 1A.

## Phase 1A live closeout record

Use this section to record the operator-observed live gate rather than relying on chat history. Do not mark a row PASS until the listed behavior has actually been exercised against the current Phase 1A artifact.

| Gate | Result | Date / build | Notes |
|---|---|---|---|
| Clean Java 25 build/tests | PASS | 2026-09-24 / `0.1.0-phase1a` | Operator-reported; archived test results show 45 tests, 0 failures/errors/skips after the initial-load preservation tests. Re-run after the startup-recovery tests in this closeout adjustment; the expected suite count is 49. |
| Fresh Paper default-file generation | PASS | 2026-09-24 / `0.1.0-phase1a` | Operator-confirmed. |
| Malformed config startup recovery (backup + default + revalidation) | PENDING | | Supersedes the earlier strict-refusal behavior after operator review. |
| Invalid fallback locale startup recovery (backup + default + revalidation) | PENDING | | Supersedes the earlier strict-refusal behavior after operator review. |
| Unsupported schema remains non-recoverable | PENDING | | Automatic downgrade/reset is intentionally prohibited. |
| Admission on / Protection off | PASS | 2026-09-24 / `0.1.0-phase1a` | Confirmed by fresh default startup. |
| Admission off / Protection on | PENDING | | |
| Admission on / Protection on | PASS | 2026-09-24 / `0.1.0-phase1a` | Protection foundation activation message operator-confirmed with default Admission retained. |
| Admission off / Protection off | PENDING | | |
| Standalone Admission regression | PASS | 2026-09-24 / `0.1.0-phase1a` | Operator reported all requested client connection tests passed. |
| Exact unknown-brand / near-match regression | PASS | 2026-09-24 / `0.1.0-phase1a` | Included in operator-reported client connection regression. |
| Fabric cannot be reclassified by unknown-brand rules | PASS | 2026-09-24 / `0.1.0-phase1a` | Included in operator-reported client connection regression. |
| Velocity/Geyser/Floodgate focused regression | PASS | 2026-09-24 / `0.1.0-phase1a` | Operator reported all requested client connection tests passed. |

## Protection non-regression boundary

Phase 1A must **not** change command execution, command visibility, namespaced-command behavior, suggestions/tab completion, staff notifications, or eZProtector permissions. Those are Phase 1B acceptance subjects.

## Completion gate

Phase 1A should be considered complete only after:

1. the clean Java 25 Gradle build/tests pass;
2. the patch applies cleanly to the exact supplied archive;
3. local Paper startup/config safety tests pass;
4. the focused standalone Admission regression passes; and
5. the focused BadWolfMC Velocity-network regression passes.

Only then should the project proceed to Phase 1B.
