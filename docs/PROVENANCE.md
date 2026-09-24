# Guardian / Cerberus provenance record

This file records the source/provenance boundary used for the Phase 1A implementation prepared on 2026-09-24.

## Guardian / BrandBlocker lineage

Guardian is a hard fork and substantial rewrite of BrandBlocker by Menacho, with subsequent BadWolfMC development. The Phase 1A review used the supplied legacy reference archive:

- archive: `BrandBlocker(4).zip`
- SHA-256: `624184728aa379f6f9c895679e41df6dc260051fc8615c9c146ce09d579b8907`
- declared project version: `1.9.4-26.2`
- legacy plugin authors in `plugin.yml`: Menacho, mercurialmusic
- license lineage: GPLv3

BrandBlocker is behavioral/provenance reference material. Guardian does not preserve its delayed `PlayerJoinEvent` architecture, substring-based brand matching, console-command kick path, ignored `enable` setting, or username-prefix trust model.

## Guardian Protection / eZProtector lineage

The Phase 1A review used the supplied BadWolfMC eZProtector reference archive:

- archive: `ezProtector(2).zip`
- SHA-256: `4e8d2f04ef33384cc1abe5dc2adf68c6726c43cd02b0644c34807f105643f3df`
- declared Paper project version: `1.4.0-26.2`
- repository referenced by the supplied README: `BadWolfMC/eZProtector`
- license declared by the supplied README: GPLv3

The supplied archive does not contain Git metadata, so an exact source commit SHA cannot be established from the archive itself and is deliberately **not invented here**. Phase 1A incorporates no eZProtector command-protection implementation code. Before Phase 1B incorporates source-derived implementation rather than merely reimplementing approved behavior, the exact BadWolfMC GPLv3 source commit should be recorded from an authoritative repository/archive record.

Later AGPL eZProtector continuation code remains outside Guardian's provenance boundary unless licensing is explicitly reconsidered first.

## Phase 1A source archive

The implementation patch is based on the exact supplied Guardian archive:

- archive: `Guardian(20260924-084631).zip`
- SHA-256: `2285162a29a76007ead834c4cca9c2baa7e1e5b1834cd2ec27457eb92f262aea`

The final Phase 1A patch is verified with `git apply --check` against a fresh extraction of this archive before delivery.
