package com.badwolfmc.guardian.core.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Transactional scan -> validate -> merge -> atomic catalog-write service. */
public final class ArtifactImportService {
    private final Path approvedArtifactsDirectory;
    private final ArtifactCatalogStore catalogStore;
    private final ApprovedArtifactScanner scanner;

    public ArtifactImportService(Path dataDirectory) {
        this(
            dataDirectory.resolve("approved-artifacts"),
            new ArtifactCatalogStore(dataDirectory.resolve("artifacts.yml")),
            new ApprovedArtifactScanner()
        );
    }

    ArtifactImportService(
        Path approvedArtifactsDirectory,
        ArtifactCatalogStore catalogStore,
        ApprovedArtifactScanner scanner
    ) {
        this.approvedArtifactsDirectory = approvedArtifactsDirectory;
        this.catalogStore = catalogStore;
        this.scanner = scanner;
    }

    public synchronized void ensureInputDirectory() throws ArtifactCatalogException {
        if (Files.exists(approvedArtifactsDirectory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(approvedArtifactsDirectory)
                || !Files.isDirectory(approvedArtifactsDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw new ArtifactCatalogException(
                    "approved-artifacts must be a real directory, not a symlink or other file type");
            }
            return;
        }
        try {
            Files.createDirectories(approvedArtifactsDirectory);
        } catch (IOException ex) {
            throw new ArtifactCatalogException("could not create approved-artifacts directory", ex);
        }
    }

    /** Validates existing durable content without importing candidate JARs. */
    public synchronized ArtifactCatalog validateCatalog() throws ArtifactCatalogException {
        return catalogStore.load();
    }

    /** No catalog bytes are changed unless every candidate JAR validates successfully. */
    public synchronized ArtifactImportResult scanAndMerge() throws ArtifactCatalogException {
        ensureInputDirectory();
        ArtifactCatalog existing = catalogStore.load();
        ApprovedArtifactScanner.ScanResult scan = scanner.scan(approvedArtifactsDirectory);
        ArtifactCatalog merged = existing.merge(scan.artifacts());
        int added = merged.size() - existing.size();
        boolean changed = added != 0 || !catalogStore.exists();
        if (changed) {
            catalogStore.store(merged);
        }
        return new ArtifactImportResult(
            scan.jarCount(),
            scan.artifacts().size(),
            added,
            merged.size(),
            changed
        );
    }
}
