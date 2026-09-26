package com.badwolfmc.guardian.core.artifact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable, deterministically ordered exact-artifact catalog for later policy consumption. */
public final class ArtifactCatalog {
    private final NavigableMap<String, NavigableMap<String, NavigableSet<com.badwolfmc.guardian.protocol.ArtifactSha256>>> artifacts;

    private ArtifactCatalog(
        NavigableMap<String, NavigableMap<String, NavigableSet<com.badwolfmc.guardian.protocol.ArtifactSha256>>> artifacts
    ) {
        TreeMap<String, NavigableMap<String, NavigableSet<com.badwolfmc.guardian.protocol.ArtifactSha256>>> outer = new TreeMap<>();
        artifacts.forEach((modId, versions) -> {
            TreeMap<String, NavigableSet<com.badwolfmc.guardian.protocol.ArtifactSha256>> versionCopy = new TreeMap<>();
            versions.forEach((version, hashes) -> versionCopy.put(
                version,
                Collections.unmodifiableNavigableSet(new TreeSet<>(hashes))
            ));
            outer.put(modId, Collections.unmodifiableNavigableMap(versionCopy));
        });
        this.artifacts = Collections.unmodifiableNavigableMap(outer);
    }

    public static ArtifactCatalog empty() {
        return new ArtifactCatalog(new TreeMap<>());
    }

    public static ArtifactCatalog of(Collection<ApprovedArtifact> entries) {
        Objects.requireNonNull(entries, "entries");
        TreeMap<String, NavigableMap<String, NavigableSet<com.badwolfmc.guardian.protocol.ArtifactSha256>>> map = new TreeMap<>();
        for (ApprovedArtifact entry : entries) {
            Objects.requireNonNull(entry, "entry");
            map.computeIfAbsent(entry.modId(), ignored -> new TreeMap<>())
                .computeIfAbsent(entry.version(), ignored -> new TreeSet<>())
                .add(entry.sha256());
        }
        return new ArtifactCatalog(map);
    }

    public ArtifactCatalog merge(Collection<ApprovedArtifact> additions) {
        ArrayList<ApprovedArtifact> combined = new ArrayList<>(entries());
        combined.addAll(Objects.requireNonNull(additions, "additions"));
        return of(combined);
    }

    public List<ApprovedArtifact> entries() {
        ArrayList<ApprovedArtifact> result = new ArrayList<>();
        artifacts.forEach((modId, versions) -> versions.forEach((version, hashes) ->
            hashes.forEach(hash -> result.add(new ApprovedArtifact(modId, version, hash)))));
        return List.copyOf(result);
    }

    public int size() {
        return entries().size();
    }

    public boolean contains(ApprovedArtifact artifact) {
        var versions = artifacts.get(artifact.modId());
        if (versions == null) return false;
        var hashes = versions.get(artifact.version());
        return hashes != null && hashes.contains(artifact.sha256());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ArtifactCatalog catalog && artifacts.equals(catalog.artifacts);
    }

    @Override
    public int hashCode() {
        return artifacts.hashCode();
    }
}
