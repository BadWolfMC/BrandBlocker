# Guardian / Cerberus — Phase 0A Test Series 4

This repository is the standalone Paper/Fabric feasibility spike for Guardian/Cerberus.

## Current architectural finding

Live Test Series 3 established that Fabric -> Paper custom payloads work during CONFIGURATION, but Paper -> Fabric does not complete through the supported plugin-messaging APIs because Paper never receives Fabric's configuration-stage client channel registration. Guardian deliberately does not use Fabric implementation internals, Mixins, NMS, reflection, or packet libraries to force that exchange.

## Test Series 4 fallback

This revision implements the project contract's supported fallback:

- Vanilla: pre-world allow.
- Fabric without Cerberus: pre-world `CERBERUS_REQUIRED` denial.
- Fabric with compatible Cerberus: CONFIGURATION presence check, then immediate PLAY quarantine for the fresh nonce challenge/response.
- Deliberate test manifest denial: `MANIFEST_DENIED` after a successful PLAY handshake.

See `docs/PHASE_0A_TEST_PLAN.md` for the exact four-case run.

This remains a feasibility spike, not production Guardian.
