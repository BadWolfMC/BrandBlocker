# Guardian / Cerberus

Guardian/Cerberus is currently completing the Phase 0 feasibility work for the architecture described in:

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

## Current Phase 0 settings

`guardian-paper/src/main/resources/config.yml` contains feasibility-spike values:

```yaml
phase0:
  authority: standalone
  handshake-timeout-seconds: 10
  challenge-channel-wait-ticks: 40
```

For the Phase 0B Velocity-authoritative feasibility deployment, set `authority: velocity` on Guardian-Paper and provide the same Base64-encoded 32-byte `GUARDIAN_PHASE0B_PROXY_SECRET` environment variable to both Velocity and Paper. Guardian-Velocity can still enforce proxy-side admission against a plain Paper backend when this feasibility secret is absent; a Guardian-Paper backend in `velocity` authority mode deliberately fails closed without a valid assertion. These settings are for the spike, not frozen production configuration.

## Next phase

Phase 0 feasibility is complete. The next implementation phase in the authoritative plan is Phase 1 — Foundation and BrandBlocker rewrite. Phase 0 spike settings and protocol details remain feasibility artifacts rather than frozen production configuration.

See:

- `docs/PHASE_0A_FINDINGS.md`
- `docs/PHASE_0A_TEST_PLAN.md`
- `docs/SANITY_REPORT.md`
- `docs/DEVELOPMENT.md`
