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

## Remaining runtime questions

A real Paper 26.2 server plus real 26.2 clients must still prove:

1. Fabric's `fabric` brand is visible at the Paper configuration events.
2. Cerberus's registered clientbound configuration channel appears in `PlayerConfigurationConnection#getListeningPluginChannels()` in time.
3. Paper `sendPluginMessage(...)` reaches Fabric's configuration receiver.
4. Fabric `responseSender()` reaches Paper's configuration-aware `PluginMessageListener`.
5. Waiting for the bounded response inside `AsyncPlayerConnectionConfigureEvent` does not deadlock or prevent the network callback from completing.
6. `PlayerConnectionValidateLoginEvent#kickMessage(...)` produces the distinct denial before `PlayerJoinEvent`/world entry.
7. aborted/denied connections clean up session state.

The live matrix in `PHASE_0A_TEST_PLAN.md` is the Phase 0A exit test. Phase 0B should not begin until the four required cases pass.

## Deliberately provisional choices

For the spike only, the protocol uses:

- protocol version `1`;
- channels `guardian:challenge` and `guardian:response`;
- a 16-byte nonce;
- a 1024-byte maximum payload;
- at most 8 manifest entries;
- a tiny binary encoding with `GUA0` magic;
- a five-second timeout;
- a synthetic `guardian-phase0a-deny` manifest entry for the deliberate denial case.

These are test fixtures, not production protocol commitments. The authoritative project plan intentionally defers final wire format, channel names, limits, timeout, signing, and complete manifest semantics.
