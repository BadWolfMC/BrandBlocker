# Phase 2.5 verification — artifact identity and approved-artifact catalog

## Current gate status — 2026-09-26

The Phase 2.5 source implementation and source-level hardening checks are complete. The authoritative clean Java 25 / Gradle 9.7.1 test/build gate must still be run by the operator because the implementation sandbox available for this pass has Java 21 and cannot download the repository's Gradle 9.7.1 distribution.

The source tree contains **102 `@Test` cases**, up from the Phase 2 baseline of 85.

## 1. Required clean automated gate

From the repository root with Java 25 active:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Required result:

- Gradle wrapper reports 9.7.1;
- JVM reports Java 25;
- all 102 tests pass with zero failures/errors;
- Guardian Paper and Velocity artifacts build;
- Cerberus Fabric builds; and
- produced artifacts report project version `0.1.0-phase2.5`.

## 2. New automated coverage

Phase 2.5 adds or extends coverage for:

- stable SHA-256 of known bytes and file-size bounding;
- required protocol-v1 artifact-hash capability;
- digest round-trip and deterministic canonical serialization;
- malformed digest length/model rejection;
- required hashes for top-level archives and forbidden hashes on non-applicable entries;
- realistic large nested manifests with only the top-level archive hashed;
- built-in, directory, nested, and mixed-origin serialization;
- path-privacy regression;
- multiple versions and multiple hashes for one ID/version;
- idempotent rescanning;
- add-new and delete-input-with-history-retained behavior;
- malformed/non-Fabric and malformed-ZIP rejection;
- bounded artifact count, ZIP-entry count, and `fabric.mod.json` reading;
- flat-directory behavior;
- duplicate/conflicting catalog input;
- deterministic catalog output; and
- existing Phase 2 nonce/canonical/protocol response validation with the revised required capability mask.

Tests create realistic fixture JARs at runtime. No third-party mod artifact is committed under test resources.

## 3. Source-level checks completed in the implementation sandbox

Because Java 25/Gradle 9.7.1 could not be provisioned without network access, these checks are supporting evidence only and do not replace Section 1:

- `guardian-protocol` and `guardian-core` main sources compile directly under the available Java 21 compiler, demonstrating no syntax/type regression in the pure-Java boundary;
- all **48** protocol/core `@Test` methods execute successfully under a lightweight local JUnit-compatible runner against those compiled classes;
- the two Paper resource tests execute successfully with the raw packaged resources, including the new `/guardian artifacts scan` command/permission declaration;
- a standalone smoke harness imported generated Fabric JAR fixtures, verified deterministic/idempotent catalog merge and retained entries after input deletion, and round-tripped a hashed manifest; and
- repository inspection found no bundled approved-artifact JARs or third-party mod fixtures.

## 4. Focused manual verification worth performing

After the clean automated gate passes, keep live testing narrow:

1. On Paper, start Guardian once and confirm `approved-artifacts/` is created and no candidate JAR is imported automatically. Put one or two known Fabric mod JARs in the directory, run `/guardian artifacts scan`, compare a generated hash with PowerShell `Get-FileHash -Algorithm SHA256`, delete the input JAR, scan again, and confirm its catalog entry remains.
2. Make one ordinary standalone Paper Fabric + Cerberus connection and confirm the revised protocol-v1 response is accepted and quarantine releases normally.
3. Make one ordinary Velocity-authoritative Fabric + Cerberus connection and confirm proxy attestation succeeds and Paper still does not re-attest the client.

The Phase 2 failure matrix does not need to be repeated unless one of these basic regressions fails.

## 5. Security/hardening closeout criteria

Before promoting Phase 2.5 to complete, confirm:

- no absolute client filesystem path appears in manifest encoding/logging;
- no approved-artifact JAR is packaged in Guardian/Cerberus outputs;
- the scanner contains no classloading/execution/extraction/install path;
- malformed input cannot partially rewrite `artifacts.yml`;
- deleting scanner inputs never deletes catalog history;
- BRIDGE-003, BRIDGE-004, and BRIDGE-005 remain unchanged; and
- documentation continues to state that reported SHA-256 is not hostile-client remote attestation.
