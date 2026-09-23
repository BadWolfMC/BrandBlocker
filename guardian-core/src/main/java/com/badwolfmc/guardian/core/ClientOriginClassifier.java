package com.badwolfmc.guardian.core;

/**
 * Classifies connection origin from supported platform evidence before Java brand classification.
 *
 * <p>Deliberately accepts no username or username-prefix input. A Floodgate-style name therefore
 * has no security authority in this classifier.</p>
 */
public final class ClientOriginClassifier {
    private ClientOriginClassifier() {
    }

    public static ClientClassification classify(
        boolean geyserBedrock, boolean floodgateBedrock, String javaBrand
    ) {
        if (geyserBedrock || floodgateBedrock) {
            return ClientClassification.BEDROCK;
        }
        return BrandClassifier.classify(javaBrand);
    }
}
