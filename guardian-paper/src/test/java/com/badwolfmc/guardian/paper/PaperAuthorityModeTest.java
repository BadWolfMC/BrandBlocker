package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaperAuthorityModeTest {
    @Test
    void parsesSupportedAuthorityModes() {
        assertEquals(PaperAuthorityMode.STANDALONE, PaperAuthorityMode.parse("standalone"));
        assertEquals(PaperAuthorityMode.VELOCITY, PaperAuthorityMode.parse(" VELOCITY "));
    }

    @Test
    void rejectsUnknownAuthorityMode() {
        assertThrows(IllegalArgumentException.class, () -> PaperAuthorityMode.parse("auto"));
    }
}
