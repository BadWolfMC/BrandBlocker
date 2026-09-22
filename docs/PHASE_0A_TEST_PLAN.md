# Phase 0A — Test Series 4: supported PLAY-quarantine fallback

## Why this test exists

Test Series 3 proved that a stock Fabric 26.2 client can send Guardian custom payloads to Paper 26.2 during CONFIGURATION even when Fabric reports the server did not advertise the channel.

It also proved the reverse path is blocked at Paper's supported plugin-messaging layer: Paper's `PlayerConfigurationConnection.sendPluginMessage(...)` only emits the payload when the client channel appears in `getListeningPluginChannels()`, while the Paper/Fabric CONFIGURATION registration exchange never populates that set.

Fabric's code contains an internal mechanism that can force its channel registration packet, but it is not public Fabric API and is deliberately out of bounds for Guardian/Cerberus.

This test therefore exercises the project contract's preferred supported fallback:

1. Keep Fabric/Cerberus **presence + protocol detection in CONFIGURATION**.
2. Deny Fabric-without-Cerberus before world entry as `CERBERUS_REQUIRED`.
3. Let a Fabric client with compatible Cerberus enter PLAY in an immediate quarantine.
4. Cerberus sends a PLAY presence on join.
5. Guardian sends the fresh nonce challenge using ordinary Paper plugin messaging.
6. Cerberus responds using Fabric PLAY networking.
7. Guardian validates and either releases quarantine or disconnects with a distinct reason.

## Run these four cases

### 1. Vanilla
Expected:
- `JAVA_VANILLA`
- pre-world `ALLOW / VANILLA_POLICY`
- no Guardian PLAY quarantine
- normal join

### 2. Fabric without Cerberus
Expected:
- `JAVA_FABRIC`
- no CONFIGURATION presence
- pre-world `DENY / CERBERUS_REQUIRED`
- player never reaches PLAY/world

### 3. Fabric + Cerberus
Expected CONFIGURATION logs:
- CONFIGURATION presence received
- compatible Cerberus presence detected
- connection allowed to proceed specifically for PLAY handshake

Expected PLAY logs:
- quarantine active immediately on join
- Cerberus PLAY presence received
- `listeningChannels` should contain `guardian:challenge`
- Guardian PLAY challenge sent
- Cerberus response received
- `ALLOW / CERBERUS_VERIFIED`
- quarantine released

### 4. Fabric + Cerberus deliberate denial
Launch client with:

`-Dcerberus.phase0a.deny=true`

Expected:
- same successful CONFIGURATION presence and PLAY challenge/response
- `DENY / MANIFEST_DENIED`
- distinct player-facing denial message

## Optional diagnostics

- timeout: `-Dcerberus.phase0a.suppressResponse=true`
- incompatible protocol: `-Dcerberus.phase0a.protocol=99`
- malformed response: `-Dcerberus.phase0a.malformed=true`

## Scope of the quarantine

This is deliberately a feasibility quarantine, not production hardening. While pending it cancels movement, block interaction, entity interaction, block breaking/placing, commands, chat, inventory clicks, item drop/pickup, and incoming damage. Production quarantine semantics should be reviewed separately if this transport test succeeds.
