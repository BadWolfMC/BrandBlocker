# Phase 0A implementation findings

## Static feasibility conclusion

The current Paper 26.2 and Fabric 26.2 public APIs expose all pieces needed to attempt the standalone pre-world handshake without NMS, reflection, Mixins on the server, or a packet library.

Paper 26.2 provides:

- `PlayerConfigurationConnection`, which is a `PluginMessageRecipient`, exposes the client brand, the channels advertised by the client, and `sendPluginMessage(...)`;
- the `PluginMessageListener` overload accepting `PlayerConnection`, explicitly documented for both joined and CONFIGURATION-stage players;
- `AsyncPlayerConnectionConfigureEvent`, whose completion gates connection progression and occurs before world entry;
- `PlayerConnectionValidateLoginEvent`, which can deny progression with an Adventure kick message when configuration is completing.

Fabric/Fabric API 26.2 provides:

- configuration-specific payload registration through `PayloadTypeRegistry.clientboundConfiguration()` and `serverboundConfiguration()`;
- `ClientConfigurationNetworking.registerGlobalReceiver(...)`;
- configuration receiver contexts with `responseSender()`;
- configuration-stage channel registration/negotiation needed for a non-Fabric server peer to discover supported payload channels.

This makes the intended architecture API-feasible on paper. It does **not** substitute for the required live interoperability test.

## First live smoke-test result and lifecycle correction

The first standalone Paper 26.2 smoke test established several useful facts:

- vanilla was admitted normally;
- Fabric without Cerberus was classified as Fabric and denied before world entry with the distinct `CERBERUS_REQUIRED` message;
- the client brand was still `null` at `PlayerConnectionInitialConfigureEvent` but was available by the later async configuration barrier;
- `getListeningPluginChannels()` was empty in the observed configuration snapshots, including for a client where Cerberus had initialized.

The original prototype therefore made its Cerberus-presence decision at the wrong point in the lifecycle. `AsyncPlayerConnectionConfigureEvent` occurs after configuration work and is appropriate as a final pre-world barrier, not as the point at which to begin channel negotiation. Treating an empty channel snapshot there as proof that Cerberus was absent produced a false `CERBERUS_REQUIRED` result.

Revision `0.0.2-phase0a` changes the experiment without adding a delay or unsupported API:

1. Paper registers `guardian:presence` and `guardian:response` as incoming channels.
2. Fabric registers the presence payload as serverbound configuration traffic.
3. Cerberus sends presence from `ClientConfigurationConnectionEvents.START`, where Fabric explicitly permits packet sending.
4. Guardian receives presence through Paper's configuration-aware plugin-message callback and sends the challenge immediately.
5. The late async Paper event only classifies/finalizes the already-started exchange.
6. `getListeningPluginChannels()` remains diagnostic information only.

This produces the intended state split: no presence = `CERBERUS_REQUIRED`; presence plus unsupported protocol = `CERBERUS_PROTOCOL_UNSUPPORTED`; presence/challenge without response = `CERBERUS_TIMEOUT`; malformed response = `MANIFEST_INVALID`; valid response proceeds to manifest evaluation.

## Remaining runtime questions

A real Paper 26.2 server plus real 26.2 clients must still prove:

1. Fabric's `fabric` brand is visible at the Paper configuration events.
2. Paper's incoming `guardian:presence` registration is visible to Fabric at configuration `START`.
3. Fabric presence reaches Paper's configuration-aware `PluginMessageListener`.
4. Paper `sendPluginMessage(...)` reaches Fabric's configuration receiver after presence.
5. Fabric `responseSender()` reaches Paper's configuration-aware `PluginMessageListener`.
6. Any bounded wait still needed at the late async barrier does not deadlock or prevent the network callback from completing.
7. `PlayerConnectionValidateLoginEvent#kickMessage(...)` continues to deny before `PlayerJoinEvent`/world entry.
8. aborted/denied connections clean up session state.

The live matrix in `PHASE_0A_TEST_PLAN.md` is the Phase 0A exit test. Phase 0B should not begin until the four required cases pass.

## Deliberately provisional choices

For the spike only, the protocol uses:

- protocol version `1`;
- channels `guardian:presence`, `guardian:challenge`, and `guardian:response`;
- a 16-byte nonce;
- a 1024-byte maximum payload;
- at most 8 manifest entries;
- a tiny binary encoding with `GUA0` magic;
- a five-second timeout;
- a synthetic `guardian-phase0a-deny` manifest entry for the deliberate denial case.

These are test fixtures, not production protocol commitments. The authoritative project plan intentionally defers final wire format, channel names, limits, timeout, signing, and complete manifest semantics.
