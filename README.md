# Guardian / Cerberus

Guardian/Cerberus is being developed against the architecture described in:

`docs/Guardian_Cerberus_Authoritative_Project_Plan.md`

That document is the living project contract and should be updated when an implementation result intentionally changes an architectural assumption.

## Phase 0A status: complete

Live Paper 26.2 / Fabric 26.2 testing established:

- Fabric -> Paper CONFIGURATION payload delivery works.
- Missing Cerberus and incompatible Cerberus protocol can be denied before world entry.
- Stock supported Paper/Fabric 26.2 high-level APIs do not provide the clean reverse CONFIGURATION channel-registration path needed for the entire nonce exchange.
- The selected standalone design is therefore hybrid:
  - classification + Cerberus presence/protocol gate in CONFIGURATION;
  - compatible Cerberus enters immediate bounded PLAY quarantine;
  - nonce challenge/response completes in PLAY;
  - the player is released only after verification.

The hybrid has successfully produced `CERBERUS_VERIFIED`, `MANIFEST_DENIED`, `CERBERUS_TIMEOUT`, and `MANIFEST_INVALID` in live tests.

No NMS, reflection, Fabric implementation internals, Mixins, or packet-library workaround is used.

## Phase 0B status: complete

Live Velocity/Paper/Geyser/Floodgate testing has confirmed the intended network architecture:

- Guardian-Velocity completes Fabric + Cerberus admission entirely during Velocity's awaited CONFIGURATION lifecycle and denies missing Cerberus before world entry.
- Security-sensitive Cerberus channels are consumed at the proxy rather than forwarded to Paper.
- Guardian-Velocity sends a short-lived HMAC-authenticated `guardian:proxy-admission` assertion to Guardian-Paper.
- Guardian-Paper in `velocity` authority mode verifies the trusted proxy result and does not independently re-attest or reevaluate client policy.
- Supported Geyser/Floodgate APIs classify real Bedrock connections before Java brand classification; Bedrock receives no Cerberus challenge.
- Floodgate username prefixes have no classification authority.
- Backend Floodgate can sanity-check a proxy assertion claiming `BEDROCK` when Floodgate forwarding/key sharing is configured.
- Backend switches reuse one admission for the lifetime of the proxy connection, while a full disconnect/reconnect creates a new admission session and, when required, a new Cerberus challenge.
- Standalone Guardian-Paper remains valid without Velocity, Geyser, or Floodgate and retains the Phase 0A hybrid CONFIGURATION + bounded PLAY-quarantine path.

For BadWolfMC's Velocity network, Guardian-Velocity is therefore the preferred admission authority; standalone Guardian-Paper remains a supported independent deployment mode.

## Phase 1A status: build-verified implementation candidate

Phase 1A replaces the feasibility-oriented Paper host with Guardian's production foundation while retaining the live-proven Phase 0 transport boundaries. The candidate now includes:

- `guardian-core` as the platform-neutral Admission domain;
- `guardian-protection` as a separate platform-neutral Protection domain;
- Guardian-Paper as the host/adaptor that can enable Admission, Protection, both, or neither;
- schema-versioned Paper configuration using explicit `ALLOW`, `DENY`, and `REQUIRE_CERBERUS` client actions;
- exact normalized allowlist/denylist rules for otherwise unknown Java brands;
- versioned locale resources rendered with Adventure/MiniMessage and safe unparsed internal placeholders;
- parse -> validate -> immutable candidate -> atomic activation infrastructure, with timestamped startup backups before safe default recovery and strict non-mutating reload failure that retains the prior valid snapshot;
- Guardian permission roots under `guardian.admission.*` and `guardian.protection.*`; and
- provenance/verification documentation for the BrandBlocker rewrite and the Phase 1B eZProtector boundary.

The default Paper configuration is now:

```yaml
schema-version: 1
features:
  admission:
    enabled: true
  protection:
    enabled: false
admission:
  authority: standalone
```

`GUARDIAN_PHASE0B_PROXY_SECRET` remains intentionally transitional for the retained Velocity -> Paper assertion transport. Its final provisioning/configuration UX is deferred to Phase 5 rather than being frozen by Phase 1A.

Phase 1A deliberately does **not** implement Guardian Protection command filtering or the later full Cerberus/mod-policy engine. Protection enforcement is Phase 1B; named admission profiles, permission/profile resolution, and full mod policy are later Admission phases.

The Java 25 clean build/test gate and client-connection regressions are green. Before Phase 1B begins, complete the short startup-recovery smoke check recorded in `docs/PHASE_1A_VERIFICATION.md`. Active temporary implementation bridges are tracked in `docs/IMPLEMENTATION_BRIDGES.md` and must be reviewed at each phase boundary.

See:

- `docs/PHASE_0A_FINDINGS.md`
- `docs/PHASE_0A_TEST_PLAN.md`
- `docs/SANITY_REPORT.md`
- `docs/PHASE_1A_VERIFICATION.md`
- `docs/PROVENANCE.md`
- `docs/IMPLEMENTATION_BRIDGES.md`
- `docs/DEVELOPMENT.md`
