# Phase 0A sanity report

Date: 2026-09-21

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
