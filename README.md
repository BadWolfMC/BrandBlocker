# Guardian / Cerberus — Phase 0A feasibility spike

This is the intentionally minimal standalone Paper/Fabric proof-of-concept for BadWolfMC Guardian/Cerberus.

It exists to answer one question: can Paper 26.2 and a Fabric 26.2 client exchange a bounded challenge/response during Minecraft CONFIGURATION and make a distinct allow/deny decision before world entry using supported public APIs only?

## Modules

- `guardian-protocol` — dependency-free Phase 0A wire records, limits and codec.
- `guardian-core` — dependency-free brand classification and deliberate Phase 0A manifest evaluation.
- `guardian-paper` — standalone Paper 26.2 adapter and pre-world admission gate.
- `cerberus-fabric` — Fabric 26.2 client responder and tiny Loader-derived test manifest.

Velocity, Geyser/Floodgate, LuckPerms, production policy files, full mod inventory policy, hashing, signatures, database/storage, GUI and admin commands are intentionally absent.


## Build tooling

The repository Gradle Wrapper is authoritative and is pinned to Gradle 9.7.1. Do not run the build with a separately installed `gradle` executable; use the wrapper so Loom and every developer/IDE use the same Gradle release.

Windows PowerShell:

```powershell
.\gradlew.bat --version
.\gradlew.bat clean test :guardian-paper:jar :cerberus-fabric:build
```

Linux/macOS:

```bash
./gradlew --version
./gradlew clean test :guardian-paper:jar :cerberus-fabric:build
```

If an IDE was previously importing this checkout with Gradle 9.2.0, reload/reimport the Gradle project after updating the checkout so it forgets the stale Tooling API connection.

## Paper lifecycle used

1. `PlayerConnectionInitialConfigureEvent` creates the initial admission session and records the configuration-stage brand for diagnostics.
2. `AsyncPlayerConnectionConfigureEvent` is the bounded pre-world barrier. Vanilla is allowed immediately. Fabric must advertise the Cerberus challenge channel; Guardian sends a challenge and waits up to five seconds for a response.
3. Paper's configuration-aware `PluginMessageListener#onPluginMessageReceived(String, PlayerConnection, byte[])` receives the Cerberus response.
4. `PlayerConnectionValidateLoginEvent` applies the already-computed decision using `kickMessage(...)`; no packets are sent from this validation event.
5. `PlayerConnectionCloseEvent` removes abandoned/denied session state.

The Paper configuration event package is public API but version-sensitive/experimental in the 26.2 line. This spike therefore targets 26.2 only and deliberately carries no 26.3 compatibility code.

## Fabric lifecycle used

Cerberus registers typed configuration payloads through `PayloadTypeRegistry.clientboundConfiguration()` / `serverboundConfiguration()` and receives the challenge with `ClientConfigurationNetworking.registerGlobalReceiver(...)`. The response uses the receiver context's `responseSender()`.

The test manifest intentionally contains only Loader-known versions for `cerberus`, `fabricloader`, and `minecraft`, plus an optional deliberate-deny marker. It is not the production manifest design.

## Diagnostic switches

- `-Dcerberus.phase0a.deny=true` adds the Phase 0A deny marker and should produce `MANIFEST_DENIED`.
- `-Dcerberus.phase0a.protocol=99` sends a recognizable but unsupported protocol version and should produce `CERBERUS_PROTOCOL_UNSUPPORTED`.
- `-Dcerberus.phase0a.suppressResponse=true` advertises Cerberus normally but deliberately does not answer the challenge, and should produce `CERBERUS_TIMEOUT`.
- `-Dcerberus.phase0a.malformed=true` returns an intentionally invalid payload and should produce `MANIFEST_INVALID`.

See `docs/PHASE_0A_TEST_PLAN.md` for the live matrix.

## Legacy lineage

Guardian is a substantial rewrite/hard-fork successor to BrandBlocker. BrandBlocker's useful product intent (brand-aware admission) informed this spike, but its delayed `PlayerJoinEvent` enforcement, substring-only matching, username-prefix Geyser bypass, and console-command kick architecture are not carried forward.

The prototype source is intended to remain GPLv3-compatible with that lineage. A production repository should carry the complete attribution/license notices required by the authoritative project contract.
