# Phase 0A findings — after Test Series 3

## Proven

- Paper 26.2 can classify the Fabric brand by configuration finalization.
- Fabric 26.2 -> Paper 26.2 custom payload delivery works during CONFIGURATION through supported APIs.
- Guardian can receive a Cerberus presence payload pre-world and distinguish Cerberus absence from presence.
- Paper can deny the connection before world entry with a structured reason/message.
- Vanilla admission works normally.

## CONFIGURATION blocker

Bidirectional CONFIGURATION plugin messaging does **not** interoperate cleanly between stock Paper 26.2 and stock Fabric API 26.2 using only the supported high-level APIs tested here.

Paper's `PlayerConfigurationConnection.sendPluginMessage(...)` sends only when the requested channel is already present in the connection's listening-channel set. In live tests that set remains empty.

Fabric's configuration networking implementation sends its initial client channel-registration packet after receiving a registration packet from the server. With a vanilla/Paper configuration flow, Fabric sees the server brand packet and starts configuration networking without sending that initial registration packet. The method and payload used to force this behavior live under Fabric implementation internals rather than its public API.

Guardian/Cerberus will not use those internals, Mixins, NMS, reflection, or a packet library to defeat that boundary.

## Test Series 4

The next experiment uses the contract-approved fallback:
- CONFIGURATION remains authoritative for Fabric classification and Cerberus presence/protocol detection.
- Missing/incompatible Cerberus can still be denied pre-world.
- Only the fresh nonce challenge/response moves to immediate PLAY under a short quarantine.

A successful Test Series 4 would prove the supported fallback transport, not restore the original all-CONFIGURATION architecture.
