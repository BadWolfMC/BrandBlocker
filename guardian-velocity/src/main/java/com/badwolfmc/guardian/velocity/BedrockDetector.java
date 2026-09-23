package com.badwolfmc.guardian.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.UUID;

final class BedrockDetector {
    private final ProxyServer server;
    private final Logger logger;

    BedrockDetector(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    BedrockDetection detect(UUID playerId) {
        BedrockSignal geyser = queryGeyser(playerId);
        BedrockSignal floodgate = queryFloodgate(playerId);
        return new BedrockDetection(geyser, floodgate);
    }

    private BedrockSignal queryGeyser(UUID playerId) {
        if (server.getPluginManager().getPlugin("geyser").isEmpty()) {
            return BedrockSignal.UNAVAILABLE;
        }
        try {
            return GeyserBedrockLookup.isBedrockPlayer(playerId)
                ? BedrockSignal.BEDROCK
                : BedrockSignal.NOT_BEDROCK;
        } catch (RuntimeException | LinkageError ex) {
            logger.warn("Guardian Phase 0B.3 could not query the optional Geyser API for {}.", playerId, ex);
            return BedrockSignal.UNAVAILABLE;
        }
    }

    private BedrockSignal queryFloodgate(UUID playerId) {
        if (server.getPluginManager().getPlugin("floodgate").isEmpty()) {
            return BedrockSignal.UNAVAILABLE;
        }
        try {
            return FloodgateBedrockLookup.isBedrockPlayer(playerId)
                ? BedrockSignal.BEDROCK
                : BedrockSignal.NOT_BEDROCK;
        } catch (RuntimeException | LinkageError ex) {
            logger.warn("Guardian Phase 0B.3 could not query the optional Floodgate API for {}.", playerId, ex);
            return BedrockSignal.UNAVAILABLE;
        }
    }
}
