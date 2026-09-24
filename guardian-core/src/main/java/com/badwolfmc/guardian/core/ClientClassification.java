package com.badwolfmc.guardian.core;

public enum ClientClassification {
    BEDROCK("bedrock"),
    JAVA_VANILLA("vanilla"),
    JAVA_OPTIFINE("optifine"),
    JAVA_FABRIC("fabric"),
    JAVA_UNKNOWN("unknown");

    private final String policyKey;

    ClientClassification(String policyKey) {
        this.policyKey = policyKey;
    }

    public String policyKey() {
        return policyKey;
    }
}
