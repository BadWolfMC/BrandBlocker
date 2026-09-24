package com.badwolfmc.guardian.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionPolicyTest {
    @Test
    void defaultsUseExplicitThreeStateClientActions() {
        AdmissionPolicy policy = AdmissionPolicy.defaults();
        assertEquals(ClientAction.ALLOW, policy.actionFor(ClientClassification.BEDROCK, null));
        assertEquals(ClientAction.ALLOW, policy.actionFor(ClientClassification.JAVA_VANILLA, "vanilla"));
        assertEquals(ClientAction.ALLOW, policy.actionFor(ClientClassification.JAVA_OPTIFINE, "optifine"));
        assertEquals(ClientAction.REQUIRE_CERBERUS,
            policy.actionFor(ClientClassification.JAVA_FABRIC, "fabric"));
        assertEquals(ClientAction.DENY, policy.actionFor(ClientClassification.JAVA_UNKNOWN, "lunarclient"));
    }

    @Test
    void allowlistAppliesOnlyToUnknownBrandsByExactNormalizedValue() {
        AdmissionPolicy policy = policy(
            ClientAction.REQUIRE_CERBERUS,
            ClientAction.DENY,
            new UnknownBrandPolicy(BrandRuleMode.ALLOWLIST, Set.of("LunarClient"))
        );

        assertEquals(ClientAction.ALLOW,
            policy.actionFor(ClientClassification.JAVA_UNKNOWN, " lunarclient "));
        assertEquals(ClientAction.DENY,
            policy.actionFor(ClientClassification.JAVA_UNKNOWN, "lunarclient-extra"));
        assertEquals(ClientAction.REQUIRE_CERBERUS,
            policy.actionFor(ClientClassification.JAVA_FABRIC, "lunarclient"),
            "raw brand rules must never override a positive Fabric classification");
    }

    @Test
    void denylistAllowsUnlistedUnknownBrandsAndDeniesListedOnes() {
        AdmissionPolicy policy = policy(
            ClientAction.REQUIRE_CERBERUS,
            ClientAction.ALLOW,
            new UnknownBrandPolicy(BrandRuleMode.DENYLIST, Set.of("badlion"))
        );

        assertEquals(ClientAction.DENY,
            policy.actionFor(ClientClassification.JAVA_UNKNOWN, "BadLion"));
        assertEquals(ClientAction.ALLOW,
            policy.actionFor(ClientClassification.JAVA_UNKNOWN, "custom-client"));
    }

    @Test
    void requireCerberusIsRejectedForNonFabricClasses() {
        assertThrows(IllegalArgumentException.class, () -> new AdmissionPolicy(Map.of(
            ClientClassification.BEDROCK, ClientAction.REQUIRE_CERBERUS,
            ClientClassification.JAVA_VANILLA, ClientAction.ALLOW,
            ClientClassification.JAVA_OPTIFINE, ClientAction.ALLOW,
            ClientClassification.JAVA_FABRIC, ClientAction.REQUIRE_CERBERUS,
            ClientClassification.JAVA_UNKNOWN, ClientAction.DENY
        ), new UnknownBrandPolicy(BrandRuleMode.ALLOWLIST, Set.of())));
    }

    @Test
    void contradictoryUnknownModeAndFallbackAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> policy(
            ClientAction.REQUIRE_CERBERUS,
            ClientAction.ALLOW,
            new UnknownBrandPolicy(BrandRuleMode.ALLOWLIST, Set.of("custom"))
        ));
        assertThrows(IllegalArgumentException.class, () -> policy(
            ClientAction.REQUIRE_CERBERUS,
            ClientAction.REQUIRE_CERBERUS,
            new UnknownBrandPolicy(BrandRuleMode.DENYLIST, Set.of())
        ));
    }

    @Test
    void admissionPermissionNamesAreBoundedAndDomainScoped() {
        assertEquals("guardian.admission.profile.staff", AdmissionPermissions.profile("staff"));
        assertEquals("guardian.admission.client.bypass.fabric", AdmissionPermissions.clientBypass("fabric"));
        assertEquals("guardian.admission.mod.bypass.sodium", AdmissionPermissions.modBypass("sodium"));
        assertTrue(AdmissionPermissions.modBypass("a".repeat(64)).length() <= AdmissionPermissions.MAX_PERMISSION_LENGTH);
        assertThrows(IllegalArgumentException.class, () -> AdmissionPermissions.profile("Staff Admin"));
        assertThrows(IllegalArgumentException.class, () -> AdmissionPermissions.modBypass("a".repeat(65)));
    }

    private static AdmissionPolicy policy(
        ClientAction fabricAction,
        ClientAction unknownAction,
        UnknownBrandPolicy unknownBrandPolicy
    ) {
        return new AdmissionPolicy(Map.of(
            ClientClassification.BEDROCK, ClientAction.ALLOW,
            ClientClassification.JAVA_VANILLA, ClientAction.ALLOW,
            ClientClassification.JAVA_OPTIFINE, ClientAction.ALLOW,
            ClientClassification.JAVA_FABRIC, fabricAction,
            ClientClassification.JAVA_UNKNOWN, unknownAction
        ), unknownBrandPolicy);
    }
}
