# Phase 0A sanity report

Date: 2026-09-22

## Completed in this environment

- Reviewed the authoritative project contract and kept the implementation within Phase 0A scope.
- Reviewed the legacy BrandBlocker source for behavior/reference only.
- Re-checked the current Paper 26.2 connection/plugin-messaging APIs and current Fabric 26.2 networking/build APIs.
- Compiled `guardian-protocol` + `guardian-core` + `tools/Phase0SelfTest.java` with the available JDK 21 as a source/logic sanity check.
- Ran the self-test with assertions enabled: `Phase0SelfTest: PASS`.
- Scanned project Java/build/resource files for server NMS, Java reflection, Velocity, Geyser/Floodgate, LuckPerms, and database dependencies; none were found.
- Verified that the prototype keeps distinct decision reasons for Cerberus missing, timeout, incompatible protocol, denied manifest, and invalid protocol/manifest data.

## Environment limitation

The execution environment used for this review provides JDK 21, does not provide Gradle, has no cached Paper/Fabric dependencies, and cannot resolve Maven repositories from shell processes. Paper/Fabric 26.2 target Java 25. Therefore a full Gradle compile of `guardian-paper` and `cerberus-fabric`, and a live Minecraft interoperability test, were not executed here.

This is not considered evidence that the runtime handshake works. The live matrix in `PHASE_0A_TEST_PLAN.md` remains the required Phase 0A exit test.

## 0.0.2 lifecycle-correction sanity pass

- Added a bounded `Presence` message to the dependency-free protocol and covered it in both JUnit source and the standalone self-test.
- Moved handshake initiation out of the late Paper async configuration event.
- Added Fabric `ClientConfigurationConnectionEvents.START` presence transmission; Fabric documents `START` as send-capable.
- Guardian now initiates the challenge only after receiving explicit Cerberus presence through Paper's configuration-aware plugin-message listener.
- Removed `getListeningPluginChannels()` as an admission requirement; it remains diagnostic only.
- Preserved distinct `CERBERUS_REQUIRED`, `CERBERUS_TIMEOUT`, `CERBERUS_PROTOCOL_UNSUPPORTED`, `MANIFEST_DENIED`, and `MANIFEST_INVALID` states.
- Recompiled and ran the dependency-free protocol/core self-test with the available JDK 21: `Phase0SelfTest: PASS`.
- Full Paper/Fabric Gradle compilation still cannot be executed in this environment because JDK 25 and dependency resolution are unavailable to shell processes.
