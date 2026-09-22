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

## Current timing defaults

`guardian-paper/src/main/resources/config.yml` contains feasibility-spike timing values:

```yaml
phase0:
  handshake-timeout-seconds: 10
  challenge-channel-wait-ticks: 40
```

These are bounded configuration values for the spike, not frozen production defaults.

## Next phase

Phase 0B will independently test the intended Velocity-authoritative topology, including the proxy CONFIGURATION lifecycle, Geyser/Floodgate classification, channel containment, trusted proxy -> backend admission assertions, and preservation of standalone Paper operation.

See:

- `docs/PHASE_0A_FINDINGS.md`
- `docs/PHASE_0A_TEST_PLAN.md`
- `docs/SANITY_REPORT.md`
- `docs/DEVELOPMENT.md`
