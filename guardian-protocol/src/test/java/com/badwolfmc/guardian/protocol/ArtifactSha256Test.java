package com.badwolfmc.guardian.protocol;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArtifactSha256Test {
    @TempDir Path temp;

    @Test
    void knownFixtureBytesProduceStableSha256() throws Exception {
        Path file = temp.resolve("fixture.jar");
        Files.writeString(file, "hello");
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            ArtifactSha256.hashRegularFile(file, 1024).hex()
        );
    }

    @Test
    void hashingIsBounded() throws Exception {
        Path file = temp.resolve("large.jar");
        Files.write(file, new byte[33]);
        assertThrows(java.io.IOException.class, () -> ArtifactSha256.hashRegularFile(file, 32));
    }
}
