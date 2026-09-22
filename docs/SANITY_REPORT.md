# Phase 0A Test Series 4 — sanity report

## Source-level checks completed

- `guardian-protocol` + `guardian-core` + `tools/Phase0SelfTest.java` compile under the available local JDK and the self-test reports `Phase0SelfTest: PASS`.
- No Guardian server source imports NMS/CraftBukkit, reflection, Velocity, Geyser/Floodgate, LuckPerms, JDBC, or database APIs.
- Cerberus contains no Fabric `impl` imports, Mixins, or direct use of Fabric's internal `RegistrationPayload`.
- Paper PLAY disconnect uses the supported Adventure `Player#kick(Component)` API.
- Fabric PLAY transport uses the public `PayloadTypeRegistry.clientboundPlay/serverboundPlay`, `ClientPlayConnectionEvents.JOIN`, and `ClientPlayNetworking` APIs.
- CONFIGURATION presence remains on the public `ClientConfigurationNetworking` API proven by Test Series 3.

## Build limitation in this environment

A complete Gradle build was not possible here because the execution environment has JDK 21, the project targets Java 25, and the Gradle wrapper distribution/dependencies cannot be downloaded from this sandbox. The user's local Java 25/Gradle environment remains the authoritative compile check for the platform modules.

## What this revision intentionally does not claim

The PLAY fallback transport has not yet been live-tested in this revision. In particular, the next live test must confirm that Paper's PLAY `getListeningPluginChannels()` contains `guardian:challenge` after Cerberus enters PLAY and sends its presence. HandShaker and normal Fabric/Paper PLAY networking make this a reasonable experiment, but Guardian will not call it proven before the runtime test.
