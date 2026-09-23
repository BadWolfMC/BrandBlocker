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

## Phase 0B status: in progress

Live Velocity testing has now confirmed that Guardian-Velocity can complete the Cerberus nonce challenge/response entirely during Velocity's awaited CONFIGURATION lifecycle, hold the client before PLAY/world entry, and consume the Cerberus security channels instead of forwarding them to Paper.

Checkpoint 0B.2 adds the next feasibility proof: a short-lived HMAC-authenticated `guardian:proxy-admission` assertion from Guardian-Velocity to Guardian-Paper. Paper now has an explicit `phase0.authority` mode so Velocity-authoritative backends verify that assertion instead of independently re-attesting Cerberus.

The same checkpoint also retries a temporarily unavailable client brand at Paper's final pre-world validation event, to test whether standalone Guardian-Paper can operate behind otherwise plain Velocity without special proxy integration.

## Current Phase 0 settings

`guardian-paper/src/main/resources/config.yml` contains feasibility-spike values:

```yaml
phase0:
  authority: standalone
  handshake-timeout-seconds: 10
  challenge-channel-wait-ticks: 40
```

For the Phase 0B.2 Velocity-authoritative Paper test, set `authority: velocity` on Guardian-Paper and provide the same Base64-encoded 32-byte `GUARDIAN_PHASE0B_PROXY_SECRET` environment variable to both Velocity and Paper. Guardian-Velocity can still enforce proxy-side admission against a plain Paper backend when this feasibility secret is absent; a Guardian-Paper backend in `velocity` authority mode deliberately fails closed without a valid assertion. These settings are for the spike, not frozen production configuration.

## Next phase

Phase 0B still needs to prove the trusted assertion ordering/spoof boundary live, then Geyser/Floodgate classification and backend switching semantics before the phase can close.

See:

- `docs/PHASE_0A_FINDINGS.md`
- `docs/PHASE_0A_TEST_PLAN.md`
- `docs/SANITY_REPORT.md`
- `docs/DEVELOPMENT.md`
