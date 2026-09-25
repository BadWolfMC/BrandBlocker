package com.badwolfmc.guardian.paper.locale;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeSnapshot;
import com.badwolfmc.guardian.protection.ProtectionDecision;
import com.badwolfmc.guardian.protection.ProtectionReason;
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

    public Component renderProtectionDenial(
        GuardianRuntimeSnapshot snapshot,
        ProtectionDecision decision,
        String command
    ) {
        return render(snapshot, protectionDenialKey(decision.reason()), protectionResolver(decision, "", command));
    }

    public Component renderProtectionNotification(
        GuardianRuntimeSnapshot snapshot,
        ProtectionDecision decision,
        String playerName,
        String command
    ) {
        return render(
            snapshot,
            protectionNotificationKey(decision.reason()),
            protectionResolver(decision, playerName, command)
        );
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

    static String protectionDenialKey(ProtectionReason reason) {
        return switch (reason) {
            case EXECUTION_DENIED -> "protection.command-denied";
            case NAMESPACE_DENIED -> "protection.namespace-denied";
            default -> throw new IllegalArgumentException("Protection reason is not an execution denial: " + reason);
        };
    }

    static String protectionNotificationKey(ProtectionReason reason) {
        return switch (reason) {
            case EXECUTION_DENIED -> "protection.notify.command-denied";
            case NAMESPACE_DENIED -> "protection.notify.namespace-denied";
            default -> throw new IllegalArgumentException("Protection reason has no notification template: " + reason);
        };
    }

    private static TagResolver protectionResolver(
        ProtectionDecision decision,
        String playerName,
        String command
    ) {
        return TagResolver.builder()
            .resolver(Placeholder.unparsed("player", playerName))
            .resolver(Placeholder.unparsed("command", command))
            .resolver(Placeholder.unparsed("root", decision.root().value()))
            .resolver(Placeholder.unparsed("reason", decision.reason().name()))
            .build();
    }
}
