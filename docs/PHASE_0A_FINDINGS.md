# Phase 0A findings — complete

## Outcome

Phase 0A is complete.

The original all-CONFIGURATION standalone design was tested rather than assumed. The stronger design does not interoperate bidirectionally through the supported high-level Paper 26.2 / Fabric 26.2 APIs used by the prototype, but the contract-approved hybrid does.

## Proven

Live testing established:

- Vanilla is classified and allowed before world entry.
- Fabric 26.2 -> Paper 26.2 custom payload delivery works during CONFIGURATION through supported APIs.
- Guardian can receive a Cerberus CONFIGURATION presence payload before world entry.
- Fabric without Cerberus is denied pre-world as `CERBERUS_REQUIRED`.
- Cerberus with an incompatible protocol is denied pre-world as `CERBERUS_PROTOCOL_UNSUPPORTED`.
- A compatible Cerberus client may proceed into a bounded PLAY quarantine.
- Paper/Fabric PLAY plugin messaging supports the nonce challenge/response cleanly.
- A valid test manifest produces `CERBERUS_VERIFIED` and releases quarantine.
- The deliberate deny marker produces `MANIFEST_DENIED`.
- A suppressed response produces `CERBERUS_TIMEOUT`.
- A malformed response produces `MANIFEST_INVALID`.
- No NMS, reflection, Mixins, Fabric implementation classes, or packet-library dependency is needed.

## CONFIGURATION limitation

Paper's supported `PlayerConfigurationConnection.sendPluginMessage(...)` path sends only when the requested outgoing channel is already visible in the connection's listening-channel set.

During the tested Paper/Fabric CONFIGURATION flow, Fabric's relevant client channel registration is not exposed to Paper in time to satisfy that requirement. Fabric contains implementation-level machinery capable of forcing its registration payload, but Guardian/Cerberus will not depend on Fabric `impl` classes, Minecraft implementation payload classes, Mixins, NMS, reflection, or packet-library workarounds.

The selected standalone design therefore preserves CONFIGURATION for the parts it can do reliably and moves only the bidirectional nonce exchange into PLAY.

## Selected standalone Paper 26.2 flow

1. CONFIGURATION begins.
2. Guardian classifies the Java client.
3. Fabric clients must send Cerberus presence during CONFIGURATION.
4. Missing Cerberus -> `CERBERUS_REQUIRED`, denied before world entry.
5. Unsupported Cerberus protocol -> `CERBERUS_PROTOCOL_UNSUPPORTED`, denied before world entry.
6. Compatible Cerberus -> enter PLAY quarantined.
7. Guardian waits a bounded/configurable interval for `guardian:challenge` to appear in Paper's PLAY listening channels.
8. Guardian sends a fresh nonce challenge.
9. Cerberus responds with the nonce, protocol version, and Phase 0A manifest.
10. Guardian validates and either releases quarantine or disconnects with the structured reason.

## Timing observation

On the local test environment, Paper consistently exposed `guardian:challenge` one tick after Cerberus PLAY presence.

Test Series 5 therefore:

- makes the total PLAY handshake timeout configurable;
- replaces the single next-tick retry with a bounded once-per-tick channel-registration wait;
- keeps the fast path unchanged when the channel is already available.

## Hardening item parked for Phase 6

Research during Phase 0A identified signed official client-artifact identity as a possible later compliance-hardening mechanism.

The project may explore a canonical Cerberus JAR digest signed by a release-only private key so Guardian can distinguish an official signed artifact identity from an ordinary modified/self-built artifact.

This must not be described as hostile-client remote attestation: a deliberately modified client could potentially report a valid hash/signature pair taken from an official release while executing different code.

See `Guardian_Cerberus_Authoritative_Project_Plan.md` for the authoritative wording.
