# Phase 0A local smoke-test plan

This repository is deliberately a feasibility spike, not a production Guardian release.

## Build

Requirements: JDK 25, a current Gradle 9.x environment (Gradle 9.7.1 is current as of this spike), and internet access for Paper/Fabric dependencies.

Windows PowerShell:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :cerberus-fabric:build
```

Linux/macOS:

```bash
./gradlew clean test :guardian-paper:jar :cerberus-fabric:build
```

Expected artifacts:

- `guardian-paper/build/libs/guardian-paper-0.0.1-phase0a.jar`
- `cerberus-fabric/build/libs/cerberus-fabric-0.0.1-phase0a.jar`

## Test server/client

- Server: standalone Paper 26.2, Java 25, no Velocity.
- Client A: vanilla 26.2.
- Client B: Fabric 26.2 + Fabric API, no Cerberus.
- Client C: Fabric 26.2 + Fabric API + Cerberus.
- Client D: same as C, launched with JVM argument `-Dcerberus.phase0a.deny=true`.

The prototype uses provisional channels `guardian:challenge` and `guardian:response`. They are Phase 0A choices only; the authoritative project contract intentionally defers final channel naming and wire format.

## Required matrix

| Case | Expected Guardian result | Expected world entry |
|---|---|---|
| Vanilla | `ALLOW / VANILLA_POLICY` | yes |
| Fabric, no Cerberus | `DENY / CERBERUS_REQUIRED` | no |
| Fabric + Cerberus | `ALLOW / CERBERUS_VERIFIED` | yes |
| Fabric + Cerberus + `-Dcerberus.phase0a.deny=true` | `DENY / MANIFEST_DENIED` | no |

Additional diagnostic checks:

- Launch Cerberus with `-Dcerberus.phase0a.protocol=99` and expect `CERBERUS_PROTOCOL_UNSUPPORTED`.
- Launch Cerberus with `-Dcerberus.phase0a.suppressResponse=true` and expect `CERBERUS_TIMEOUT`.
- Launch Cerberus with `-Dcerberus.phase0a.malformed=true` and expect `MANIFEST_INVALID`.

## Evidence to capture

For each connection, save the Paper log lines showing:

1. the brand visible during initial configuration;
2. the Phase 0A classification;
3. for Cerberus clients, the advertised `guardian:challenge` channel;
4. the final structured `ALLOW`/`DENY` reason;
5. whether `PlayerJoinEvent`/world entry occurs (server log is sufficient).

The critical runtime questions are:

- Is Fabric's brand (`fabric`) visible by the Paper configuration events on a real 26.2 client?
- Does Fabric receiver registration surface in `PlayerConfigurationConnection#getListeningPluginChannels()` before the async configuration barrier returns?
- Does Paper `sendPluginMessage` -> Fabric `ClientConfigurationNetworking` work in CONFIGURATION?
- Does Fabric `responseSender().sendPacket` -> Paper's `PluginMessageListener(PlayerConnection, ...)` work in CONFIGURATION?
- Does waiting up to five seconds inside `AsyncPlayerConnectionConfigureEvent` allow the response to arrive without deadlock?
- Does `PlayerConnectionValidateLoginEvent#kickMessage` deny before world entry with the distinct expected message?
- Does `PlayerConnectionCloseEvent` clean sessions for denied/aborted connections?

If any of those transport/lifecycle checks fails, stop Phase 0A and adjust only with supported Paper/Fabric APIs. Do not introduce NMS or packet interception.
