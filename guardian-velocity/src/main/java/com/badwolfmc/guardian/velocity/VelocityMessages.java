package com.badwolfmc.guardian.velocity;

import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Bundled locale renderer for the retained Phase 0 Velocity adapter.
 *
 * <p>Velocity configuration/localization administration is finalized in Phase 5. Phase 1A still
 * removes player-facing Java literals so the adapter observes Guardian's localization invariant.</p>
 */
final class VelocityMessages {
    private static final String RESOURCE = "locales/en_us.properties";
    private static final int SCHEMA_VERSION = 1;
    private static final Set<String> REQUIRED_KEYS = Set.of(
        "admission.denied",
        "admission.proxy-assertion-required",
        "admission.proxy-assertion-invalid",
        "admission.cerberus-required",
        "admission.cerberus-timeout",
        "admission.cerberus-protocol-unsupported",
        "admission.manifest-denied",
        "admission.manifest-invalid",
        "admission.client-denied",
        "admission.configuration-error",
        "meta.help-url"
    );

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, String> values;

    private VelocityMessages(Map<String, String> values) {
        this.values = values;
    }

    static VelocityMessages load() {
        Properties properties = new Properties();
        try (InputStream stream = VelocityMessages.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("missing bundled Guardian locale resource " + RESOURCE);
            }
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("could not load bundled Guardian locale resource " + RESOURCE, ex);
        }

        String schema = properties.getProperty("schema-version");
        if (!Integer.toString(SCHEMA_VERSION).equals(schema == null ? null : schema.trim())) {
            throw new IllegalStateException(RESOURCE + " must declare schema-version=" + SCHEMA_VERSION);
        }

        LinkedHashMap<String, String> loaded = new LinkedHashMap<>();
        for (String key : REQUIRED_KEYS) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(RESOURCE + " is missing required locale key " + key);
            }
            loaded.put(key, value);
        }
        return new VelocityMessages(Map.copyOf(loaded));
    }

    Component render(
        DecisionReason reason,
        ClientClassification classification
    ) {
        String classificationValue = classification == null ? "unknown" : classification.policyKey();
        TagResolver resolver = TagResolver.builder()
            .resolver(Placeholder.unparsed("classification", classificationValue))
            .resolver(Placeholder.unparsed("reason", reason.name()))
            .resolver(Placeholder.unparsed("help_url", values.get("meta.help-url")))
            .build();
        return miniMessage.deserialize(values.get(keyFor(reason)), resolver);
    }

    private static String keyFor(DecisionReason reason) {
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
