package com.badwolfmc.guardian.velocity;

record BedrockDetection(BedrockSignal geyser, BedrockSignal floodgate) {
    boolean bedrock() {
        return geyser == BedrockSignal.BEDROCK || floodgate == BedrockSignal.BEDROCK;
    }

    boolean disagrees() {
        return geyser != BedrockSignal.UNAVAILABLE
            && floodgate != BedrockSignal.UNAVAILABLE
            && geyser != floodgate;
    }
}
