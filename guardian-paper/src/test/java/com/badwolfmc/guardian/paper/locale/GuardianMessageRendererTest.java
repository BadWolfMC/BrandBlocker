package com.badwolfmc.guardian.paper.locale;

import com.badwolfmc.guardian.core.AdmissionPolicy;
import com.badwolfmc.guardian.core.ClientClassification;
import com.badwolfmc.guardian.core.DecisionReason;
import com.badwolfmc.guardian.paper.PaperAuthorityMode;
import com.badwolfmc.guardian.paper.config.GuardianPaperSettings;
import com.badwolfmc.guardian.paper.config.GuardianRuntimeSnapshot;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GuardianMessageRendererTest {
    @Test
    void untrustedPlaceholderValuesCannotInjectMiniMessageEvents() {
        String attemptedInjection = "<click:run_command:'/op me'>click me</click>";
        GuardianPaperSettings settings = new GuardianPaperSettings(
            1, true, false, "en_us", attemptedInjection,
            PaperAuthorityMode.STANDALONE, 10, 40, AdmissionPolicy.defaults());
        GuardianLocaleCatalog catalog = new GuardianLocaleCatalog(
            "en_us",
            Map.of("admission.cerberus-required", "<red>Need Cerberus</red> <help_url>"),
            Map.of("admission.cerberus-required", "fallback")
        );
        GuardianRuntimeSnapshot snapshot = new GuardianRuntimeSnapshot(settings, catalog);

        Component rendered = new GuardianMessageRenderer().renderDecision(
            snapshot, DecisionReason.CERBERUS_REQUIRED, ClientClassification.JAVA_FABRIC);

        assertNoClickEvents(rendered);
    }

    @Test
    void everyDenyReasonHasALocaleKeyMapping() {
        for (DecisionReason reason : DecisionReason.values()) {
            assertNotNull(GuardianMessageRenderer.keyFor(reason));
        }
    }

    private static void assertNoClickEvents(Component component) {
        assertNull(component.clickEvent(), "safe placeholder must not create a click event");
        component.children().forEach(GuardianMessageRendererTest::assertNoClickEvents);
    }
}
