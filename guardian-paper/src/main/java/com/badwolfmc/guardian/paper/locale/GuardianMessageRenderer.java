package com.badwolfmc.guardian.paper.locale;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class GuardianMessageRenderer {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Component renderDecision(
        GuardianRuntimeSnapshot snapshot,
        DecisionReason reason,
        ClientClassification classification
    ) {
        String classificationValue = classification == null ? "unknown" : classification.policyKey();
        return render(snapshot, keyFor(reason), TagResolver.builder()
            .resolver(Placeholder.unparsed("classification", classificationValue))
            .resolver(Placeholder.unparsed("reason", reason.name()))
            .resolver(Placeholder.unparsed("help_url", snapshot.settings().helpUrl()))
            .build());
    }

    public Component render(GuardianRuntimeSnapshot snapshot, String key, TagResolver resolver) {
        String template = snapshot.localeCatalog().template(key);
        return miniMessage.deserialize(template, resolver);
    }

    static String keyFor(DecisionReason reason) {
        return switch (reason) {
            case PROXY_ASSERTION_REQUIRED -> "admission.proxy-assertion-required";
            case PROXY_ASSERTION_INVALID -> "admission.proxy-assertion-invalid";
            case CERBERUS_REQUIRED -> "admission.cerberus-required";
            case CERBERUS_TIMEOUT -> "admission.cerberus-timeout";
            case CERBERUS_PROTOCOL_UNSUPPORTED -> "admission.cerberus-protocol-unsupported";
            case MANIFEST_DENIED -> "admission.manifest-denied";
            case MANIFEST_INVALID -> "admission.manifest-invalid";
            case CLIENT_DENIED -> "admission.client-denied";
            case CONFIGURATION_ERROR -> "admission.configuration-error";
            default -> "admission.denied";
        };
    }
}
