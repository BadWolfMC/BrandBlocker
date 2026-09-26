package com.badwolfmc.cerberus.manifest;

import com.badwolfmc.guardian.protocol.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class FabricManifestCollector {
    private FabricManifestCollector() {}
    public static Manifest collect() {
        FabricLoader loader=FabricLoader.getInstance();
        List<ManifestEntry> entries=new ArrayList<>();
        for(ModContainer mod:loader.getAllMods()) {
            String parent=mod.getContainingMod().map(p->p.getMetadata().getId()).orElse(null);
            entries.add(new ManifestEntry(mod.getMetadata().getId(),mod.getMetadata().getVersion().getFriendlyString(),parent,originKind(mod)));
        }
        Manifest manifest=new Manifest(version(loader,"minecraft"),version(loader,"fabricloader"),version(loader,"cerberus"),GuardianProtocol.KNOWN_CAPABILITIES,entries);
        return ManifestCanonicalizer.canonicalize(manifest);
    }
    static OriginKind originKind(ModContainer mod) {
        if ("builtin".equals(mod.getMetadata().getType())) return OriginKind.BUILTIN;
        ModOrigin origin = mod.getOrigin();
        if (origin.getKind() == ModOrigin.Kind.NESTED) return OriginKind.NESTED;
        if (origin.getKind() != ModOrigin.Kind.PATH) return OriginKind.MIXED_OR_UNKNOWN;
        boolean directory=false, archive=false;
        for(Path path:origin.getPaths()){if(Files.isDirectory(path))directory=true;else archive=true;}
        if(directory&&!archive)return OriginKind.DIRECTORY;
        if(archive&&!directory)return OriginKind.ARCHIVE;
        return OriginKind.MIXED_OR_UNKNOWN;
    }
    private static String version(FabricLoader loader,String id){
        return loader.getModContainer(id)
            .map(ModContainer::getMetadata)
            .map(m->m.getVersion().getFriendlyString())
            .orElseThrow(() -> new IllegalStateException("Required Fabric Loader mod container missing: " + id));
    }
}
