# Guardian & Cerberus
## Authoritative Project Plan and Implementation Contract

**Project:** BadWolfMC Guardian / Cerberus
**Document status:** Living implementation contract; Phase 0A complete
**Initial target:** Minecraft / Paper 26.2, Java 25
**Future target:** 26.3 after Paper 26.3 reaches a stable API
**Date:** 2026-09-22

---

## 1. Purpose and authority

This document records the agreed architecture, constraints, threat model, implementation phases, deployment modes, and acceptance criteria for the Guardian/Cerberus project.

It is intended to serve as the primary project-source reference during implementation and as handoff context between development chats.

Where this document uses **MUST**, **MUST NOT**, **SHOULD**, or **MAY**, those terms describe project requirements rather than informal suggestions.

If later implementation work intentionally changes a decision in this document, the change should be recorded in this document or a successor implementation contract rather than allowed to drift silently.

This is a **broad project contract**, not a frozen wire-protocol specification. Some low-level decisions are intentionally deferred until the Phase 0 feasibility prototypes establish which public APIs are cleanly usable.

---

## 2. Project summary

Guardian is a server-side client-policy enforcement system intended to replace and substantially expand BadWolfMC's existing BrandBlocker plugin.

Cerberus is Guardian's companion client-side Fabric mod.

The central problem is that a Paper server can usually identify a client's self-reported brand but cannot directly inspect a Fabric client's installed mods. Cerberus will use Fabric Loader's public API to enumerate the client's loaded mods and report a constrained manifest to Guardian when Guardian policy requires attestation.

Guardian will then evaluate that manifest against the player's configured policy and either admit or deny the connection.

The project is designed to preserve the practical purpose of BrandBlocker: stopping accidental, inattentive, or low-effort rule violations. It is **not** intended to provide hardware-backed remote attestation or to defeat a determined attacker who modifies their client specifically to lie to Guardian.

---

## 3. Project goals

Guardian/Cerberus MUST:

1. Preserve the useful client-brand enforcement currently provided by BrandBlocker.
2. Allow approved Fabric clients when Cerberus is present and the reported mod manifest satisfies Guardian policy.
3. Clearly distinguish Fabric-without-Cerberus from Fabric-with-Cerberus-but-disallowed-mods.
4. Support Java vanilla, approved Java client brands, Fabric + Cerberus, and Bedrock through Geyser/Floodgate as distinct first-class client classifications.
5. Support a standalone Paper deployment.
6. Support an enhanced Velocity + Paper deployment without making Velocity mandatory.
7. Prefer pre-world admission decisions using supported CONFIGURATION-stage APIs.
8. Avoid NMS, reflection into implementation internals, server-side Mixins, packet-library dependencies, and version-specific internal classes.
9. Use only supported Paper, Velocity, Fabric, Java, Geyser/Floodgate, and optional permissions-provider APIs.
10. Keep the player experience simple: a normal Fabric user should only need to install Cerberus and approved mods.
11. Provide specific, understandable denial reasons.
12. Keep all security-sensitive trust assumptions explicit.
13. Be open-source and preserve appropriate BrandBlocker attribution/license obligations.
14. Be maintainable across Minecraft/Paper/Fabric updates.
15. Minimize collection and retention of client information.

---

## 4. Explicit non-goals

Guardian/Cerberus MUST NOT be represented as:

- a tamper-proof anti-cheat;
- proof that the player's complete JVM or operating system is unmodified;
- proof that no Java agent, native injection, custom launcher, or hidden modification exists;
- hardware-backed remote attestation;
- a guarantee that an adversarial client cannot spoof a Java client brand;
- a replacement for ordinary server/network security;
- a replacement for a firewall around Velocity backend servers.

A determined user who patches Cerberus, Fabric Loader, the Minecraft client, or the networking path can potentially falsify what the server sees.

The product goal is **effective policy enforcement for cooperative and ordinary clients plus a meaningful barrier against low-effort circumvention**, which matches the historical purpose of BrandBlocker.

---

## 5. BrandBlocker lineage

Guardian is a hard fork and substantial rewrite of BrandBlocker.

The existing BrandBlocker implementation currently:

- operates as a Paper plugin;
- checks client brand after `PlayerJoinEvent`;
- delays the check by 20 ticks;
- uses configured brand substrings for whitelist/blacklist behavior;
- supports a bypass permission;
- uses a configured Geyser username prefix as a bypass;
- executes a console kick command for denied clients.

Guardian MUST preserve the useful product behavior while replacing the implementation architecture.

Guardian SHOULD NOT preserve the following legacy implementation patterns:

- post-join delayed enforcement when an earlier supported connection phase is available;
- username-prefix trust for Bedrock detection;
- substring matching as the only client classification mechanism;
- console-command dispatch as the primary disconnect mechanism;
- generic denial messages for materially different failure modes;
- configuration settings that are present but not actually enforced.

The Guardian README MUST retain appropriate attribution to the original BrandBlocker project and author.

---

## 6. Repository and module architecture

The project SHOULD use a single Gradle multi-project repository.

Recommended structure:

```text
Guardian/
├── guardian-core/
├── guardian-protocol/
├── guardian-paper/
├── guardian-velocity/
├── cerberus-fabric/
└── docs/
```

### 6.1 guardian-core

`guardian-core` MUST contain platform-neutral domain logic, including:

- client classifications;
- policy models;
- policy resolution results;
- manifest models;
- manifest evaluation;
- denial/allow reason models;
- validation rules;
- common configuration-domain objects.

It MUST NOT depend on Paper, Bukkit, Velocity, Fabric, Minecraft implementation classes, Geyser, or Floodgate.

### 6.2 guardian-protocol

`guardian-protocol` MUST contain platform-neutral protocol definitions, including:

- protocol version;
- message types;
- canonical manifest representation;
- challenge/response structures;
- nonce/session identifiers;
- limits;
- serialization rules;
- compatibility/capability negotiation.

It SHOULD have no Paper, Velocity, Fabric, Bukkit, or Minecraft dependency.

### 6.3 guardian-paper

`guardian-paper` is the standalone-capable Paper adapter.

It MUST be capable of acting as the authoritative Guardian enforcement point when no Guardian-Velocity instance is providing a trusted admission result.

### 6.4 guardian-velocity

`guardian-velocity` is an optional Velocity adapter.

When configured as authoritative, it SHOULD perform initial classification, Cerberus attestation, and policy evaluation once per proxy connection before the player is admitted to a backend.

### 6.5 cerberus-fabric

`cerberus-fabric` is the client-side Fabric mod.

It MUST:

- use public Fabric/Fabric Loader APIs;
- inventory Loader-known mods;
- respond only to supported Guardian protocol requests;
- avoid unnecessary UI or player interaction;
- avoid collecting or reporting unrelated machine information.

---

## 7. Supported deployment modes

Guardian MUST support at least the following deployment modes.

### 7.1 Standalone Paper

```text
Client
  ↕
Guardian-Paper
  ↕
Paper
```

Guardian-Paper is authoritative.

Fabric clients requiring attestation communicate directly with Guardian-Paper.

### 7.2 Velocity-authoritative network

```text
Client
  ↕
Guardian-Velocity
  ↕
Velocity
  ↕
Guardian-Paper
  ↕
Paper backends
```

Guardian-Velocity is authoritative for the network login.

Guardian-Paper verifies a trusted proxy admission assertion and MUST NOT independently initiate a redundant Cerberus attestation for ordinary backend switching.

### 7.3 Paper behind Velocity without Guardian-Velocity

If technically supported by the Phase 0 prototypes, Guardian-Paper MAY operate behind an otherwise transparent Velocity proxy without the Guardian-Velocity module.

This is useful for generic deployments but MUST NOT compromise correctness or force unsupported packet/NMS workarounds.

### 7.4 Configuration authority

A deployment MUST have one authoritative policy evaluator for a connection.

In Velocity-authoritative mode:

- policy definitions belong to Guardian-Velocity;
- backend Guardian-Paper instances verify the proxy's trusted admission result;
- backend copies MUST NOT independently reinterpret the same connection using potentially divergent policy files.

This prevents proxy/backend split-brain.

Standalone fallback behavior MAY have its own local Paper policy configuration, but the active deployment mode must be explicit and diagnosable.

---

## 8. Client classification model

Guardian MUST classify the connection before deciding whether Cerberus is required.

Conceptual flow:

```text
Connection
    ↓
Determine trusted connection origin
    ├── BEDROCK
    │       ↓
    │   Bedrock policy
    │   Cerberus not applicable
    │
    └── JAVA
            ↓
       Determine Java client class
            ├── VANILLA
            ├── OPTIFINE
            ├── FABRIC
            └── UNKNOWN
                    ↓
               policy action
```

Initial client classes SHOULD include:

```text
BEDROCK
JAVA_VANILLA
JAVA_OPTIFINE
JAVA_FABRIC
JAVA_UNKNOWN
```

The classification model SHOULD be extensible so future client types can be added without rewriting the policy engine.

Cerberus remains Fabric-only unless a future project decision explicitly changes that.

---

## 9. Geyser and Floodgate

Geyser/Floodgate support is a first-class Guardian integration, not a bypass hack.

Guardian MUST NOT trust the configured Bedrock username prefix as proof that a connection is Bedrock.

When the relevant APIs are available:

- `GeyserApi#isBedrockPlayer(UUID)` MAY be used to identify a Geyser Bedrock connection;
- `FloodgateApi#isFloodgatePlayer(UUID)` MAY be used to identify a Floodgate player;
- backend Floodgate data MAY be used when proxy Floodgate forwarding is configured correctly.

A positively classified Bedrock connection MUST NOT receive a Cerberus challenge.

A Java player whose username resembles the configured Floodgate prefix MUST NOT be classified as Bedrock solely because of that name.

In Velocity-authoritative BadWolfMC deployment, Geyser/Floodgate classification at the proxy SHOULD be authoritative for connection origin.

Guardian-Paper MAY sanity-check the proxy assertion against backend Floodgate state when backend Floodgate information is available.

Unexpected disagreement between trusted proxy classification and backend Floodgate classification SHOULD be logged prominently and MUST have deterministic handling.

---

## 10. Policy model

Guardian policy determines what a client classification must do.

Conceptually:

```text
Client classification
        ↓
Resolved Guardian profile
        ↓
Policy action
        ├── ALLOW
        ├── DENY
        └── REQUIRE_CERBERUS
```

A default policy might conceptually represent:

```yaml
bedrock:
  action: allow

vanilla:
  action: allow

optifine:
  action: allow

fabric:
  action: require-cerberus

unknown:
  action: deny
```

The exact configuration syntax is deferred.

Fabric/Cerberus policies SHOULD eventually support:

- required mods;
- allowed mods;
- denied mods;
- unknown-mod behavior;
- acceptable versions/version predicates;
- optional artifact hash rules;
- rules for contained/nested mods;
- protocol compatibility requirements;
- Cerberus minimum/maximum supported versions where necessary.

Policy evaluation MUST be deterministic and independently testable in `guardian-core`.

---

## 11. Profile and permission resolution

Pre-world enforcement occurs before a normal Bukkit `Player` permission context necessarily exists.

Therefore Guardian MUST NOT assume that Bukkit permissions can always resolve a pre-login profile.

Recommended resolution order:

```text
1. Explicit configured identity/UUID override, if any
2. Supported pre-login-capable permission/profile provider
3. Default Guardian profile
```

LuckPerms SHOULD be a first-class optional integration because its API can asynchronously load user data by UUID before a player is fully online.

If no pre-login-capable provider is installed:

- Guardian MUST still function using a default policy;
- permission-selected profiles MUST NOT silently pretend to work;
- administrative emergency overrides SHOULD use a mechanism that is valid before login, such as explicit UUID configuration.

The exact generic profile-provider SPI MAY be defined during implementation.

---

## 12. Admission state machine

Guardian SHOULD model admission as an explicit state machine rather than scattered event handlers.

Conceptually:

```text
CONNECTED
   ↓
AUTHENTICATED
   ↓
ORIGIN_CLASSIFIED
   ↓
PROFILE_RESOLVED
   ↓
POLICY_SELECTED
   ↓
┌──────────────────────────────┐
│ Attestation required?       │
└───────────────┬──────────────┘
        no      │       yes
        ↓       │        ↓
    EVALUATE    │   CHALLENGE_SENT
        │       │        ↓
        │       │   RESPONSE_RECEIVED
        │       │        ↓
        │       └── MANIFEST_VALIDATED
        │                ↓
        └────────── POLICY_EVALUATED
                         ↓
                 ALLOW or DENY
```

Per-connection state MUST be cleaned up on disconnect, timeout, cancellation, proxy transfer termination, and plugin shutdown.

A fresh connection SHOULD require a fresh admission session.

Guardian MUST distinguish initial configuration from later Minecraft reconfiguration so a harmless reconfiguration does not accidentally cause duplicate or contradictory attestation behavior.

---

## 13. Guardian decision model

The core result SHOULD separate outcome from reason.

Example:

```text
GuardianDecision
├── outcome
│   ├── ALLOW
│   └── DENY
├── reason
└── structured details
```

Initial reason values SHOULD include at least:

```text
BEDROCK_POLICY
VANILLA_POLICY
OPTIFINE_POLICY
CERBERUS_VERIFIED
CERBERUS_REQUIRED
CERBERUS_TIMEOUT
CERBERUS_PROTOCOL_UNSUPPORTED
MANIFEST_DENIED
MANIFEST_INVALID
CLIENT_DENIED
PROFILE_RESOLUTION_FAILED
PROXY_ASSERTION_INVALID
CONFIGURATION_ERROR
```

The public/player-facing message is not itself the security decision.

Messages SHOULD be mapped from structured reasons so localization or wording changes cannot alter policy behavior.

---

## 14. Required distinction: missing Cerberus vs denied manifest

This is a hard UX and behavioral requirement.

Guardian MUST distinguish:

### Fabric detected, Cerberus absent

Result:

```text
DENY
reason: CERBERUS_REQUIRED
```

The player-facing message should clearly state that Fabric is supported but Cerberus is required.

### Cerberus present, manifest disallowed

Result:

```text
DENY
reason: MANIFEST_DENIED
```

The player-facing message should state that Cerberus responded successfully but one or more mods or versions are not permitted.

These conditions MUST NOT collapse into the same generic denial.

Timeout, unsupported protocol, and invalid response SHOULD likewise be independently diagnosable.

---

## 15. Cerberus manifest

Cerberus SHOULD derive the manifest from Fabric Loader's public API, including `FabricLoader#getAllMods()` and `ModContainer` metadata/origin relationships.

A canonical manifest SHOULD contain only information relevant to policy enforcement, such as:

- Minecraft version;
- Fabric Loader version;
- Cerberus version;
- Guardian protocol version/capabilities;
- mod ID;
- mod version;
- relevant parent/contained relationship;
- optional artifact digest when supported and requested;
- enough source/origin classification to determine whether hashing is meaningful.

Cerberus MUST NOT report:

- absolute filesystem paths;
- Windows/macOS/Linux usernames embedded in paths;
- launch arguments;
- unrelated hardware identifiers;
- IP information;
- arbitrary files;
- unrelated machine telemetry.

### 15.1 Nested/contained mods

Cerberus SHOULD report the complete relevant Loader-known mod relationship rather than silently deleting nested mods from the manifest.

Guardian policy MAY provide administrator-friendly handling for known bundles such as Fabric API, but nested mods MUST NOT become an invisible blind spot.

The exact policy semantics for contained mods are deferred and MUST be covered by tests before release.

### 15.2 Development environments

Fabric development environments can have directory/classpath origins rather than ordinary release JARs.

Guardian/Cerberus MUST define deterministic behavior for non-hashable or development-style origins.

Production policies MAY deny development-environment manifests by default while allowing a deliberately configured developer/staff exception.

---

## 16. Artifact hashes and signed release identity

Artifact hashes are useful integrity metadata but are not remote attestation.

Guardian MAY support SHA-256 or another appropriate cryptographic digest for mod artifacts.

Hash rules SHOULD be optional rather than mandatory for every mod because exact artifact pinning increases administrative maintenance.

Hashing can help detect:

- an unexpected artifact with the same advertised mod ID/version;
- accidental local modification;
- installation of a non-approved build.

Hashing cannot prove that a hostile Cerberus client honestly hashed what is executing.

### 16.1 Signed Cerberus release identity — Phase 6 exploration

During Phase 6, the project SHOULD evaluate an optional signed-release mechanism for official Cerberus artifacts.

One candidate model is:

1. build an official Cerberus JAR;
2. compute a canonical cryptographic digest over the release artifact;
3. sign that digest with a release private key that never ships in Cerberus;
4. distribute the signed digest/signature with the official release;
5. allow Guardian to verify that the *reported* artifact identity corresponds to an official signed release.

This can provide useful compliance hardening against:

- casual modification of an official Cerberus JAR;
- accidental local changes;
- ordinary self-compiled builds that do not possess the release private key.

It MUST NOT be represented as proof that the currently executing client code is identical to the signed artifact.

A hostile client can potentially replay or falsely report the hash/signature metadata from an official signed release while executing modified code. The mechanism therefore establishes **signed release-artifact identity**, not hardware-backed or hostile-client remote attestation.

The exact digest canonicalization, signature algorithm, metadata format, build/release integration, and whether this feature is worth the maintenance cost remain deferred until Phase 6.

Guardian documentation MUST preserve these distinctions.

---

## 17. Protocol security and trust model

### 17.1 Nonces and replay protection

Every Cerberus attestation MUST be bound to a fresh connection/session challenge.

The challenge/response SHOULD include:

- protocol version;
- random nonce;
- authenticated player identity where appropriate;
- connection/session identifier;
- bounded timestamp or expiration where useful;
- manifest or manifest digest.

Guardian MUST reject stale, reused, mismatched, malformed, or oversized responses.

### 17.2 No embedded client secret as proof of honesty

Cerberus MUST NOT rely on a long-term secret embedded in the client as proof that Cerberus is genuine.

Any secret shipped inside open client software should be assumed recoverable.

TOTP-like schemes or HMAC secrets embedded in Cerberus would not solve the hostile-client trust problem and SHOULD NOT be used for that purpose.

### 17.3 Server authentication to Cerberus

It is useful for Cerberus to authenticate Guardian before disclosing a mod manifest.

A server-held private signing key with a client-pinned public trust anchor is the preferred conceptual model.

For BadWolfMC, Cerberus MAY ship with the BadWolfMC Guardian public key/trust anchor so the ordinary user experience remains "install Cerberus and connect."

The exact public-key algorithm, trust-store format, multi-server/general-public distribution model, and key-rotation process are deferred until protocol design.

Private keys MUST NOT be committed to source control or embedded in Cerberus.

### 17.4 Proxy-to-backend trust

In Velocity-authoritative mode, Guardian-Velocity and Guardian-Paper MAY use a server-controlled shared secret or asymmetric trust mechanism to authenticate proxy admission assertions.

Unlike a secret embedded in Cerberus, this secret has real security value because both ends are infrastructure controlled by the server operator.

Proxy assertion data SHOULD be connection-scoped and short-lived.

A client MUST NOT be able to submit a payload that Guardian-Paper interprets as a valid proxy assertion.

Guardian-Velocity MUST consume/intercept its client protocol messages rather than blindly forwarding security-sensitive channels to backend servers.

---

## 18. Velocity backend security

Guardian does not replace network security.

Velocity-backed Paper servers SHOULD use Velocity modern forwarding where applicable and SHOULD be protected so untrusted clients cannot connect directly to backend servers.

Guardian-Paper MUST NOT treat a claimed proxy assertion as trustworthy merely because a packet/channel name resembles Guardian-Velocity traffic.

The Guardian threat model assumes the operator follows normal Velocity backend hardening practices.

---

## 19. Protocol versioning and compatibility

Guardian protocol version MUST be separate from plugin/mod release versions.

Example:

```text
Guardian-Paper 1.4.2
Guardian-Velocity 1.4.2
Cerberus 1.3.7
Protocol 3
```

Compatible release versions MAY speak the same protocol.

The handshake SHOULD support capability negotiation so optional additions do not automatically require a hard protocol break.

Incompatible Cerberus versions MUST produce a clear `CERBERUS_PROTOCOL_UNSUPPORTED` result rather than a generic timeout.

The disconnect message SHOULD provide an administrator-configured download/help URL or other clear remediation information.

---

## 20. Minecraft version compatibility

Initial implementation target:

```text
Minecraft 26.2
Paper 26.2 stable API
Java 25
Fabric/Fabric API compatible with 26.2
```

The project SHOULD NOT carry speculative 26.3-alpha compatibility code during initial development.

After 26.3 Paper API stabilizes:

1. complete the 26.2 architecture and tests;
2. assess API changes;
3. port Guardian/Cerberus;
4. update the compatibility matrix;
5. avoid retaining unnecessary compatibility shims for unreleased or abandoned APIs.

Cerberus releases are expected to be Minecraft-version-aware because Fabric client mod compatibility is inherently tied to the target Minecraft environment.

---

## 21. Standalone Paper networking — Phase 0A result

Phase 0A established the supported Paper 26.2 / Fabric 26.2 behavior rather than assuming full bidirectional CONFIGURATION interoperability.

### 21.1 Proven CONFIGURATION behavior

Using supported public APIs:

- Fabric 26.2 can send Guardian/Cerberus custom payloads to Paper 26.2 during CONFIGURATION.
- Cerberus can therefore announce a bounded presence payload before world entry.
- Guardian-Paper can classify the client and distinguish:
  - Fabric without Cerberus;
  - Cerberus with an unsupported protocol;
  - compatible Cerberus that is eligible to continue to the nonce handshake.
- Paper can deny missing/incompatible Cerberus before world entry with a structured reason and direct supported disconnect message.

### 21.2 Paper -> Fabric CONFIGURATION limitation on 26.2

The all-CONFIGURATION challenge/response design is **not** cleanly available through the supported high-level APIs tested on stock Paper 26.2 and stock Fabric API 26.2.

Paper's supported plugin-message send path requires the client to have advertised the outgoing channel in Paper's listening-channel set.

In the tested Paper/Fabric CONFIGURATION flow, Fabric's client channel registration is not exposed to Paper in time to satisfy that requirement. Fabric contains implementation-level machinery capable of forcing registration, but Guardian/Cerberus MUST NOT depend on Fabric `impl` classes, Minecraft implementation payload classes, Mixins, NMS, reflection, or packet-library workarounds merely to preserve an all-CONFIGURATION aesthetic.

### 21.3 Selected standalone hybrid

The selected Paper 26.2 standalone design is therefore:

```text
CONFIGURATION
    ↓
classify client
    ↓
Fabric?
    ├── no  → ordinary classification policy
    └── yes
         ↓
    Cerberus CONFIGURATION presence
         ├── absent              → CERBERUS_REQUIRED → deny pre-world
         ├── protocol unsupported→ CERBERUS_PROTOCOL_UNSUPPORTED → deny pre-world
         └── compatible
                ↓
           enter PLAY quarantined
                ↓
           wait bounded time for PLAY channel registration
                ↓
           fresh nonce challenge
                ↓
           Cerberus response + test/manifest validation
                ↓
           ALLOW and release quarantine
           or DENY with structured reason
```

The PLAY quarantine MUST remain bounded and MUST prevent meaningful interaction until the final decision is made.

Handshake timeout and PLAY channel-registration wait MUST be configurable and bounded. Exact production defaults remain subject to later hardening.

This hybrid is an intentional supported architecture, not a reintroduction of BrandBlocker's delayed post-join enforcement. Only a client that has already positively identified compatible Cerberus during CONFIGURATION is permitted to enter the temporary PLAY quarantine.

NMS MUST NOT be introduced merely to move the nonce exchange back into CONFIGURATION.

The 26.3 port SHOULD re-test CONFIGURATION interoperability rather than permanently assuming the 26.2 limitation still exists.

---

## 22. Velocity configuration-phase networking

Guardian-Velocity SHOULD attempt to perform the authoritative Cerberus handshake during Velocity's supported configuration-stage lifecycle.

Phase 0B must prove:

- client ↔ proxy custom payload exchange;
- deterministic hold/release behavior;
- clear proxy-origin disconnect reasons;
- security-sensitive channels are not leaked to backends;
- server switching does not cause unnecessary re-attestation;
- proxy admission state can be securely conveyed to Guardian-Paper.

Velocity support remains optional even if it becomes the preferred BadWolfMC production deployment.

---

## 23. Session and caching semantics

A successful Cerberus attestation SHOULD be scoped to a single client/proxy connection.

It SHOULD NOT be persisted as a long-lived "this player is trusted" database record.

Reason:

- the mod environment can change when Minecraft restarts;
- a new connection is cheap to attest;
- long-lived trust records would weaken the relationship between the current process and the current decision.

In Velocity-authoritative mode, the successful result MAY be cached for the lifetime of the proxy connection so Alpha → Beta → Gamma → Delta server changes do not repeatedly inventory the same running client.

A new proxy login SHOULD require a new session/challenge.

---

## 24. Failure semantics

Guardian MUST define failure behavior explicitly.

### 24.1 Attestation-required client

If policy requires Cerberus and Guardian cannot obtain a valid response within the configured limit, the connection SHOULD fail closed with a specific reason.

### 24.2 Invalid manifest/protocol data

Malformed, duplicate, impossible, oversized, replayed, or invalidly signed/session-bound data MUST be rejected.

### 24.3 Unsupported protocol

An unsupported but otherwise recognizable Cerberus protocol MUST produce a compatibility-specific denial.

### 24.4 Invalid policy configuration

Policy/configuration files MUST be validated before becoming active.

Reload SHOULD use an atomic "parse → validate → replace active snapshot" pattern.

A failed reload MUST leave the prior known-good configuration active.

Startup behavior for a completely invalid initial configuration MUST be explicit and prominently logged. The final implementation contract must decide whether the platform adapter:
- refuses Guardian initialization;
- blocks policy-requiring client classes;
- or provides an operator-selected strict startup mode.

It MUST NOT silently fall back to permissive behavior without an explicit documented policy.

### 24.5 Dependency/integration loss

If an optional integration such as LuckPerms, Geyser, or Floodgate disappears or becomes unavailable, Guardian MUST have deterministic behavior and a clear diagnostic.

It MUST NOT reinterpret a Bedrock player as "Fabric missing Cerberus" because an integration failed mid-classification.

---

## 25. Payload and denial-of-service limits

The protocol MUST define hard limits before production release, including at least:

- maximum protocol payload size;
- maximum mod count;
- maximum mod ID length;
- maximum version string length;
- maximum nested relationship depth;
- maximum hash count/size;
- maximum outstanding handshake time;
- maximum duplicate/retry behavior.

The implementation MUST reject oversized input before expensive processing where possible.

Manifest serialization SHOULD be compact and deterministic.

Compression MAY be considered only if necessary; it SHOULD NOT be added merely because it is available.

---

## 26. Client-brand behavior

Brand information is a useful policy signal, not cryptographic proof.

Guardian SHOULD normalize brands deterministically and SHOULD avoid accidental substring behavior that lets an unrelated string satisfy an allow rule merely because it contains an approved token.

The exact brand-rule syntax is deferred, but exact normalized values and explicitly requested patterns are preferable to implicit substring matching.

A null/empty/unknown brand MUST have explicit policy behavior.

Unknown brand must not default to "allow because detection failed" unless the administrator deliberately configures that outcome.

---

## 27. Privacy and logging

Guardian/Cerberus should collect the least information necessary to enforce policy.

Default logs SHOULD prefer summaries such as:

```text
Player X classified as JAVA_FABRIC
Profile: member
Cerberus protocol: 1
Manifest entries: 24
Decision: DENY / MANIFEST_DENIED
Disallowed: examplemod 1.2.3
```

Default logging SHOULD NOT dump:

- absolute paths;
- full manifest contents when not needed;
- unrelated system information;
- launch arguments;
- hardware identifiers.

Verbose/debug logging MAY expose more diagnostic manifest detail but must be clearly opt-in.

No external telemetry is required for the initial project.

A database is not required for the core product unless later operational requirements justify one.

---

## 28. Player-facing messages

All denial states SHOULD have distinct, useful messages.

Messages SHOULD include enough information to remediate ordinary mistakes without exposing unnecessary security internals.

Representative categories:

- unsupported client;
- Fabric requires Cerberus;
- Cerberus timed out;
- Cerberus version/protocol incompatible;
- one or more mods not approved;
- required mod missing;
- approved mod version mismatch;
- manifest invalid;
- Bedrock denied by policy;
- internal/configuration issue.

The project SHOULD use Adventure components where supported.

A configurable help/download URL SHOULD be supported.

The project SHOULD NOT rely on console `kick` commands when a direct supported disconnect/deny API exists.

---

## 29. Administrative surfaces

A later production phase SHOULD provide at least:

```text
/guardian status
/guardian inspect <player>
/guardian validate
/guardian reload
```

Exact syntax is deferred.

Useful diagnostics SHOULD include:

- active deployment mode;
- active policy/config snapshot;
- installed optional integrations;
- current protocol support;
- classification source;
- resolved profile;
- attestation status;
- decision/reason;
- proxy assertion status where relevant.

`reload` MUST be atomic and preserve the prior configuration if validation fails.

`validate` SHOULD validate files without changing active runtime state.

---

## 30. Public API/events

A small read-only Guardian API MAY be useful for other BadWolfMC plugins and third-party integrations.

If implemented, it SHOULD expose stable domain concepts such as:

- current client classification;
- resolved policy/profile;
- whether attestation was required;
- final Guardian decision;
- relevant immutable manifest summary.

Other plugins SHOULD NOT be given mutable access to internal admission state.

This API is not required for Phase 0 or the first minimal production implementation.

---

## 31. Build and release artifacts

The multi-project build SHOULD produce distinct deployable artifacts:

```text
guardian-paper-<version>.jar
guardian-velocity-<version>.jar
cerberus-fabric-<version>.jar
```

Shared core/protocol modules SHOULD normally be implementation dependencies rather than separate administrator-installed artifacts.

CI SHOULD include:

- compilation;
- unit tests;
- protocol tests;
- policy tests;
- configuration validation tests;
- platform boundary tests where practical;
- reproducible artifact naming;
- checksums for release artifacts.

Release documentation MUST clearly identify which JAR belongs on Paper, Velocity, and the Fabric client.

---

## 32. Licensing and attribution

Guardian should remain compatible with BrandBlocker's GPLv3 lineage.

The project SHOULD retain GPLv3 for Guardian unless legal review later establishes and justifies another compatible arrangement.

The README SHOULD acknowledge that Guardian began as a hard fork and substantial rewrite of BrandBlocker by Menacho and link to the original project.

Cerberus and shared modules SHOULD use a licensing arrangement compatible with the combined repository and distribution model.

Existing copyright and license notices that must legally be retained MUST remain present.

---

# 33. Implementation phases

## Phase 0A — Standalone Paper/Fabric feasibility spike — COMPLETE

**Status:** Completed 2026-09-22.

**Goal:** Prove the architectural heart of the project using supported APIs only.

Phase 0A established the following live Paper 26.2 / Fabric 26.2 results:

```text
Vanilla
→ classified
→ ALLOW / VANILLA_POLICY
→ admitted normally

Fabric without Cerberus
→ no CONFIGURATION presence
→ DENY / CERBERUS_REQUIRED
→ denied before world entry

Fabric + incompatible Cerberus protocol
→ CONFIGURATION presence received
→ DENY / CERBERUS_PROTOCOL_UNSUPPORTED
→ denied before world entry

Fabric + compatible Cerberus
→ CONFIGURATION presence received
→ enter bounded PLAY quarantine
→ fresh nonce challenge/response
→ minimal test manifest validated
→ ALLOW / CERBERUS_VERIFIED
→ quarantine released

Fabric + compatible Cerberus + deliberate deny marker
→ handshake succeeds
→ DENY / MANIFEST_DENIED
→ distinct message

Fabric + compatible Cerberus + suppressed response
→ bounded quarantine
→ DENY / CERBERUS_TIMEOUT

Fabric + compatible Cerberus + malformed response
→ DENY / MANIFEST_INVALID
```

Phase 0A proved:

- Fabric 26.2 → Paper 26.2 CONFIGURATION custom-payload delivery using supported APIs;
- configuration-stage client classification/presence sufficient for pre-world missing/incompatible-Cerberus decisions;
- supported bidirectional PLAY challenge/response under quarantine;
- no NMS, reflection, Mixins, Fabric implementation classes, or packet-library dependency is required;
- distinct Cerberus absence, timeout, incompatible protocol, denied manifest, and invalid protocol-data outcomes;
- supported connection-state cleanup paths;
- client-brand availability by configuration finalization.

Phase 0A disproved the stronger assumption that the entire standalone Paper 26.2 ↔ Fabric 26.2 challenge/response can be completed bidirectionally during CONFIGURATION through the tested supported high-level APIs.

The accepted standalone architecture is the hybrid defined in Section 21.

Phase 0A intentionally excluded:

- LuckPerms;
- real whitelist files;
- hash enforcement;
- Geyser;
- Floodgate;
- Velocity;
- signing;
- production commands;
- databases;
- GUIs.

**Exit criterion:** Satisfied. The intended all-CONFIGURATION path was tested and its 26.2 limitation identified; the contract-approved supported hybrid fallback was implemented and live-tested successfully.

---

## Phase 0B — Velocity/Geyser/Floodgate feasibility spike

**Goal:** Prove the production-network architecture with one Velocity instance and one Paper backend.

Minimal topology:

```text
Java / Fabric / Bedrock clients
           ↓
Velocity
Guardian-Velocity
Geyser + Floodgate
           ↓
Paper
Guardian-Paper
Floodgate
```

Required tests:

- Cerberus ↔ Guardian-Velocity configuration-stage handshake;
- Velocity can hold/release connection cleanly;
- proxy-origin disconnect reasons display correctly;
- security-sensitive Guardian client channels do not leak to Paper;
- Velocity → Paper trusted admission assertion works;
- backend cannot accept a spoofed client assertion;
- switching/reconnecting semantics are understood;
- Bedrock is identified through Geyser/Floodgate APIs;
- Bedrock receives no Cerberus challenge;
- Java username with the Floodgate prefix does not become Bedrock;
- backend Floodgate state can sanity-check proxy classification;
- Guardian-Paper still functions in standalone authority mode when Guardian-Velocity is absent.

**Exit criterion:** Decide whether Guardian-Velocity is suitable as the preferred BadWolfMC production authority while preserving standalone Paper operation.

---

## Phase 1 — Foundation and BrandBlocker rewrite

**Goal:** Replace the BrandBlocker architecture with Guardian's platform-neutral core and modern Paper adapter.

Implement:

- Gradle multi-project structure;
- `com.badwolfmc.guardian` package namespace;
- `guardian-core`;
- `guardian-protocol`;
- `guardian-paper`;
- structured decision model;
- Java client brand classification;
- modern direct disconnect handling;
- default policy infrastructure;
- config parse/validate/snapshot lifecycle;
- logs and diagnostics;
- attribution/license updates.

Carry forward the practical ability to allow/deny non-Fabric client brands.

Do not yet implement the full Fabric policy engine unless needed for the prototype integration.

---

## Phase 2 — Cerberus and protocol v1

**Goal:** Build the real Fabric client and stable initial handshake.

Implement:

- Fabric Loader manifest enumeration;
- canonical manifest format;
- parent/contained relationships;
- Minecraft/Fabric Loader/Cerberus/protocol metadata;
- nonce challenge/response;
- payload limits;
- timeout behavior;
- version/capability negotiation;
- explicit missing-Cerberus behavior;
- privacy constraints;
- deterministic serialization.

No client-side secret may be treated as proof of honesty.

---

## Phase 3 — Guardian policy engine

**Goal:** Turn reported manifests into configurable admission decisions.

Implement:

- default policy;
- named profiles;
- allowed/denied/required mods;
- unknown-mod handling;
- version rules;
- contained-mod semantics;
- optional artifact hashes;
- classification-specific actions;
- explicit decision reasons;
- atomic reload;
- files-only validation.

Implement profile resolution:

- explicit identity overrides;
- optional LuckPerms integration;
- default fallback profile.

---

## Phase 4 — Geyser/Floodgate integration

**Goal:** Make Bedrock a first-class supported client ecosystem.

Implement:

- Geyser API integration where available;
- Floodgate API integration where available;
- Bedrock classification before Cerberus requirements;
- no username-prefix trust;
- configurable Bedrock policy;
- mismatch diagnostics;
- backend Floodgate sanity checks where relevant.

BadWolfMC's legacy username prefix may be recognized for migration diagnostics only; it MUST NOT be a trusted identity signal.

---

## Phase 5 — Velocity authoritative mode

**Goal:** Make Velocity the preferred network-edge admission point without making it mandatory.

Implement:

- Guardian-Velocity platform adapter;
- proxy-side policy authority;
- one attestation per proxy connection;
- secure proxy → backend assertions;
- server-switch session reuse;
- backend verification;
- no client impersonation of proxy channels;
- explicit deployment modes;
- policy/config ownership rules;
- operational diagnostics.

Paper standalone authority MUST remain functional.

---

## Phase 6 — Security and adversarial hardening

**Goal:** Attack the protocol and assumptions before public release.

Test and harden:

- replay;
- duplicate nonce;
- stale session;
- malformed packet;
- oversized packet;
- extreme mod count;
- extreme/nested metadata;
- duplicate mod IDs;
- fake version strings;
- invalid hashes;
- protocol downgrade attempts;
- response after timeout;
- disconnect mid-handshake;
- repeated reconnect;
- proxy assertion spoofing;
- backend direct-connect attempts;
- unknown/empty brand;
- integration disappearance;
- reconfiguration events;
- development-environment mod origins;
- malformed configuration;
- key rotation scenarios;
- PLAY-quarantine escape/interaction attempts;
- delayed PLAY channel registration and timeout boundaries.

Evaluate signed official Cerberus release identity as described in Section 16.1, including a canonical artifact digest signed by a release-only private key. Treat it only as compliance hardening against ordinary tampering/self-builds; explicitly test and document that a hostile client can potentially report valid metadata from an official release while running different code.

Add server-authentication signing to Cerberus if the protocol design confirms it is practical.

Document residual limitations honestly.

---

## Phase 7 — Operations, UX, and release hardening

**Goal:** Make the project maintainable by real administrators.

Implement/refine:

- `/guardian status`;
- `/guardian inspect`;
- `/guardian validate`;
- `/guardian reload`;
- player-facing messages;
- help/download URLs;
- clear installation docs;
- standalone Paper guide;
- Velocity network guide;
- Geyser/Floodgate guide;
- LuckPerms/profile guide;
- security/threat-model documentation;
- release checksums;
- clean-install tests;
- upgrade tests;
- CI release workflow;
- attribution and GPL materials.

Perform a final adversarial release-candidate audit.

---

## Phase 8 — Minecraft 26.3 port

**Goal:** Port only after the Paper 26.3 API is stable enough to target intentionally.

Tasks:

- update Paper target;
- update Fabric/Fabric API target;
- test CONFIGURATION behavior;
- retest Velocity configuration behavior;
- retest Geyser/Floodgate compatibility;
- remove any obsolete 26.2-only workaround rather than accumulating unnecessary compatibility code;
- rerun complete security and deployment matrices.

---

# 34. Core acceptance matrix

Before first public production release, testing SHOULD cover at minimum:

| Client / condition | Expected result |
|---|---|
| Java vanilla, allowed | Allow |
| Approved OptiFine brand | Allow |
| Unknown Java brand under deny policy | Deny |
| Fabric, no Cerberus | `CERBERUS_REQUIRED` |
| Fabric + compatible Cerberus + approved mods | Allow |
| Fabric + Cerberus + unapproved mod | `MANIFEST_DENIED` |
| Fabric + old/incompatible Cerberus | `CERBERUS_PROTOCOL_UNSUPPORTED` |
| Fabric + Cerberus timeout | `CERBERUS_TIMEOUT` |
| Fabric + malformed manifest | `MANIFEST_INVALID` |
| Fabric + replayed prior response | Deny |
| Fabric + excessive payload | Deny |
| Bedrock through Geyser/Floodgate | Bedrock policy; no Cerberus |
| Java user with Bedrock-style prefix | Remains Java |
| Bedrock denied by explicit policy | Deny |
| Velocity-approved Java switches backend | No redundant attestation |
| Spoofed client proxy assertion | Deny |
| Backend direct-connect path | Rejected by network/security posture |
| Invalid config reload | Prior config remains active |
| Optional integration missing | Deterministic documented behavior |

---

# 35. Architecture invariants

The following are hard project invariants unless explicitly revised:

1. **No NMS.**
2. **No server-side reflection into Minecraft implementation internals.**
3. **No packet-library dependency merely to reach unsupported networking internals.**
4. **No username-prefix trust for Bedrock identity.**
5. **No claim that Cerberus provides tamper-proof client attestation.**
6. **No long-term client secret treated as proof of Cerberus authenticity.**
7. **No silent permissive fallback when an attestation-required client fails attestation.**
8. **No generic message that conflates missing Cerberus with a denied manifest.**
9. **No duplicate independent policy authority in Velocity mode.**
10. **No unnecessary collection of machine or filesystem information.**
11. **No long-lived cross-restart trust cache for client manifests.**
12. **No proxy assertion channel that a normal client can impersonate.**
13. **No automatic assumption that CONFIGURATION-stage Paper↔Fabric messaging works until Phase 0A proves it.**
14. **No speculative 26.3-alpha compatibility burden in the initial 26.2 implementation.**
15. **No loss of standalone Paper capability merely because Velocity becomes the preferred production deployment.**

---

# 36. Decisions intentionally deferred

The following should remain open until the indicated implementation phase rather than being guessed prematurely:

- exact wire encoding;
- exact custom payload channel names;
- maximum protocol payload values;
- exact timeout duration;
- exact public-key signature algorithm;
- server trust-store format in Cerberus;
- BadWolfMC-only vs broader generic trust onboarding for third-party Guardian servers;
- key rotation format;
- exact proxy assertion cryptographic format;
- exact mod-version rule syntax;
- exact nested/contained-mod policy DSL;
- exact artifact hashing rules for directory/development origins;
- whether signed official Cerberus release identity is adopted;
- if adopted, its canonical digest/signature algorithm, metadata format, and release-key lifecycle;
- exact configuration file split/names;
- exact admin command syntax;
- exact behavior when the initial configuration is invalid at process startup;
- whether a public read-only Guardian API ships in v1.0;
- whether optional persistent audit storage is ever needed.

These are not forgotten requirements. They are deliberately deferred decisions.

---

# 37. Current recommended technical sources

The implementation should re-check current documentation when each phase begins because these APIs are version-sensitive.

### Paper

- Paper 26.2 `PlayerConfigurationConnection`
  https://jd.papermc.io/paper/26.2/io/papermc/paper/connection/PlayerConfigurationConnection.html
- Paper 26.2 `PlayerConnectionValidateLoginEvent`
  https://jd.papermc.io/paper/26.2/io/papermc/paper/event/connection/PlayerConnectionValidateLoginEvent.html
- Paper plugin messaging
  https://docs.papermc.io/paper/dev/plugin-messaging/

### Velocity

- Velocity plugin messaging
  https://docs.papermc.io/velocity/dev/plugin-messaging/
- Velocity backend security
  https://docs.papermc.io/velocity/security/
- Velocity modern forwarding
  https://docs.papermc.io/velocity/player-information-forwarding/
- Velocity API/Javadocs, especially configuration events
  https://jd.papermc.io/velocity/

### Fabric

- Fabric documentation
  https://docs.fabricmc.net/
- Fabric Loader API / `FabricLoader#getAllMods()`
  https://maven.fabricmc.net/docs/fabric-loader-0.18.6/net/fabricmc/loader/api/FabricLoader.html
- Fabric Loader `ModContainer`
  https://maven.fabricmc.net/docs/fabric-loader-0.18.6/net/fabricmc/loader/api/ModContainer.html

### Geyser/Floodgate

- Geyser API
  https://geysermc.org/wiki/geyser/api/
- Floodgate API
  https://geysermc.org/wiki/floodgate/api/

### LuckPerms

- Developer API usage / offline user loading
  https://luckperms.net/wiki/Developer-API-Usage

---

# 38. Handoff context for the next development chat

**Phase 0A is complete. Phase 0B is next.**

The project should continue treating this document as the primary design authority and BrandBlocker as legacy behavior/reference rather than an implementation architecture to preserve.

The standalone Paper 26.2 result is now settled:

- keep client classification and Cerberus presence/protocol detection in CONFIGURATION;
- deny missing Cerberus and incompatible protocol before world entry;
- for compatible Cerberus, enter a bounded PLAY quarantine;
- wait a bounded/configurable interval for the PLAY challenge channel to register;
- complete the nonce challenge/response in PLAY;
- release only after `CERBERUS_VERIFIED`;
- retain distinct `CERBERUS_TIMEOUT`, `MANIFEST_DENIED`, `MANIFEST_INVALID`, and compatibility outcomes;
- do not use NMS, reflection, Fabric implementation internals, Mixins, or packet-library workarounds to force all-CONFIGURATION behavior.

The immediate engineering question for Phase 0B is:

> Can Guardian-Velocity perform the authoritative Cerberus handshake cleanly during Velocity's supported CONFIGURATION lifecycle, securely convey the resulting admission to Guardian-Paper, and integrate Geyser/Floodgate classification without losing the standalone Paper hybrid?

Phase 0B must not assume that Velocity has the same registration limitation as Paper; it must test the proxy path independently.

Do **not** begin the full production policy engine, LuckPerms profile system, artifact hashing, signed Cerberus release identity, or production command/UX surfaces during Phase 0B unless minimally necessary for the feasibility experiment.

The signed-JAR/hash concept identified during Phase 0A research is intentionally parked for Phase 6 as optional compliance hardening, with the explicit limitation that it is signed release-artifact identity rather than hostile-client remote attestation.

Only after Phase 0B should implementation proceed through the broader production phases in this contract.

---

## 39. Current overall assessment

Phase 0A succeeded in its purpose.

The original stronger all-CONFIGURATION standalone assumption was tested rather than preserved by force: Fabric → Paper CONFIGURATION payload delivery works, but the tested supported Paper 26.2/Fabric 26.2 high-level APIs do not provide the clean reverse channel-registration path required for Paper → Fabric challenge delivery.

The selected standalone hybrid is proven in live testing and preserves the most valuable pre-world behavior while remaining entirely on supported APIs.

The next major technical uncertainty is the Velocity-authoritative path: whether Velocity can complete the Cerberus exchange during its awaited CONFIGURATION lifecycle, keep Guardian channels from leaking to backends, carry a trusted admission assertion to Guardian-Paper, and correctly classify Geyser/Floodgate clients.

The project should proceed to Phase 0B while preserving the distinction between **useful client-policy enforcement** and **unforgeable hostile-client attestation**.
