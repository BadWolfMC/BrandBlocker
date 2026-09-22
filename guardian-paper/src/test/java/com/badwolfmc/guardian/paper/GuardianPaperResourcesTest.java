package com.badwolfmc.guardian.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GuardianPaperResourcesTest {
    @Test
    void requiredPluginResourcesArePackaged() {
        ClassLoader loader = GuardianPaperResourcesTest.class.getClassLoader();
        assertNotNull(loader.getResource("plugin.yml"), "plugin.yml must be present");
        assertNotNull(loader.getResource("config.yml"), "config.yml must be present because saveDefaultConfig() is used");
    }
}
