package com.badwolfmc.cerberus.manifest;

import com.badwolfmc.guardian.protocol.ArtifactSha256;
import com.badwolfmc.guardian.protocol.GuardianProtocol;
import com.badwolfmc.guardian.protocol.Manifest;
import com.badwolfmc.guardian.protocol.ManifestCanonicalizer;
import com.badwolfmc.guardian.protocol.ManifestEntry;
import com.badwolfmc.guardian.protocol.OriginKind;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Collects one immutable Loader environment snapshot for the lifetime of the client process. */
public final class FabricManifestCollector {
    private static volatile Manifest cachedManifest;

    private FabricManifestCollector() {}

    public static Manifest collect() {
        Manifest snapshot = cachedManifest;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (FabricManifestCollector.class) {
            snapshot = cachedManifest;
            if (snapshot == null) {
                snapshot = collectFresh(FabricLoader.getInstance());
                cachedManifest = snapshot;
            }
            return snapshot;
        }
    }

    private static Manifest collectFresh(FabricLoader loader) {
        List<ManifestEntry> entries = new ArrayList<>();
        for (ModContainer mod : loader.getAllMods()) {
            String parent = mod.getContainingMod().map(p -> p.getMetadata().getId()).orElse(null);
            OriginKind originKind = originKind(mod);
            ArtifactSha256 digest = artifactDigest(mod, parent, originKind);
            entries.add(new ManifestEntry(
                mod.getMetadata().getId(),
                mod.getMetadata().getVersion().getFriendlyString(),
                parent,
                originKind,
                digest
            ));
        }
        Manifest manifest = new Manifest(
            version(loader, "minecraft"),
            version(loader, "fabricloader"),
            version(loader, "cerberus"),
            GuardianProtocol.KNOWN_CAPABILITIES,
            entries
        );
        return ManifestCanonicalizer.canonicalize(manifest);
    }

    static OriginKind originKind(ModContainer mod) {
        if ("builtin".equals(mod.getMetadata().getType())) {
            return OriginKind.BUILTIN;
        }
        ModOrigin origin = mod.getOrigin();
        if (origin.getKind() == ModOrigin.Kind.NESTED) {
            return OriginKind.NESTED;
        }
        if (origin.getKind() != ModOrigin.Kind.PATH) {
            return OriginKind.MIXED_OR_UNKNOWN;
        }

        Collection<Path> paths = origin.getPaths();
        if (paths.size() != 1) {
            return OriginKind.MIXED_OR_UNKNOWN;
        }
        Path path = paths.iterator().next();
        if (Files.isSymbolicLink(path)) {
            return OriginKind.MIXED_OR_UNKNOWN;
        }
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return OriginKind.DIRECTORY;
        }
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return OriginKind.ARCHIVE;
        }
        return OriginKind.MIXED_OR_UNKNOWN;
    }

    private static ArtifactSha256 artifactDigest(ModContainer mod, String parent, OriginKind originKind) {
        if (parent != null || originKind != OriginKind.ARCHIVE) {
            return null;
        }
        List<Path> paths = List.copyOf(mod.getOrigin().getPaths());
        if (paths.size() != 1) {
            throw new IllegalStateException(
                "Top-level archive mod has ambiguous original paths: " + mod.getMetadata().getId());
        }
        try {
            return ArtifactSha256.hashRegularFile(paths.getFirst(), GuardianProtocol.MAX_ARTIFACT_BYTES);
        } catch (IOException ex) {
            // Do not attach the IOException: provider exceptions can contain an absolute client path.
            // The mod id is sufficient for a fail-safe diagnostic without crossing the privacy boundary.
            throw new IllegalStateException(
                "Could not hash top-level Fabric artifact '" + mod.getMetadata().getId()
                    + "' (" + ex.getClass().getSimpleName() + ")");
        }
    }

    private static String version(FabricLoader loader, String id) {
        return loader.getModContainer(id)
            .map(ModContainer::getMetadata)
            .map(m -> m.getVersion().getFriendlyString())
            .orElseThrow(() -> new IllegalStateException("Required Fabric Loader mod container missing: " + id));
    }
}
