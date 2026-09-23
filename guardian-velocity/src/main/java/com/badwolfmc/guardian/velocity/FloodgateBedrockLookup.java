package com.badwolfmc.guardian.velocity;

import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

final class FloodgateBedrockLookup {
    private FloodgateBedrockLookup() {
    }

    static boolean isBedrockPlayer(UUID playerId) {
        FloodgateApi api = FloodgateApi.getInstance();
        if (api == null) {
            throw new IllegalStateException("Floodgate API is not available yet");
        }
        return api.isFloodgatePlayer(playerId);
    }
}
