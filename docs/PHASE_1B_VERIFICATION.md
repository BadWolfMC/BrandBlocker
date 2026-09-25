# Phase 1B verification plan — Guardian Protection

Phase 1B is not complete until the current source builds cleanly under the repository Java 25 / Gradle 9.7.1 toolchain and the player-only Protection behavior is exercised on Paper 26.2.

## 1. Clean automated gate

From the repository root on the supported JDK:

```powershell
.\gradlew.bat clean test :guardian-paper:jar :guardian-velocity:jar :cerberus-fabric:build
```

Expected:

- all tests pass;
- Guardian-Paper packages the Phase 1B locale/config resources;
- all Phase 1A Admission tests remain green;
- `guardian-protection` remains platform-neutral;
- no `ezprotector.*` compatibility permission is introduced.

## 2. Clean or deliberately migrated Paper data directory

Phase 1B adds required Protection configuration and fallback-locale keys while Guardian is still pre-release. Before the first Phase 1B startup, either:

- use a clean `plugins/Guardian/` test directory so the packaged Phase 1B defaults are generated; or
- deliberately merge the new `protection:` section and Protection locale keys into the Phase 1A files.

Do not confuse the startup recovery mechanism with an upgrade migrator. Existing structurally invalid development files are backed up before packaged defaults are restored, but Phase 1B does not add speculative schema migration code for unreleased layouts.

## 3. Independent-domain smoke gate

Repeat the four feature combinations from Phase 1A:

| Admission | Protection | Expected |
|---|---|---|
| off | off | Guardian hosts neither domain behavior |
| on | off | Admission remains unchanged; no Protection filtering |
| off | on | Protection works on standalone Paper without Velocity/Cerberus/Geyser/Floodgate |
| on | on | Both domains work independently in the same Paper host |

For the final two rows, verify startup logs include the active Protection execution/visibility/namespace modes and rule counts.

## 4. Player-only execution boundary

For a focused test, temporarily add a harmless observable root such as `say` to `protection.execution.blocked-roots`.

Verify:

1. an ordinary player running `/say guardian-player-test` is denied and sees the locale-backed Guardian message;
2. the server console running `say guardian-console-test` still executes normally;
3. a command block running `say guardian-command-block-test` still executes normally;
4. a small helper plugin using the normal programmatic command-dispatch API can execute the same command without Guardian Protection intercepting the plugin-dispatch path.

Remove the temporary `say` rule afterward. This test is important: Phase 1B deliberately listens only to Paper's player-command preprocessing event and never to `ServerCommandEvent`.

## 5. Execution normalization and bypass

With `plugins` blocked for execution:

- `/plugins`, `/PLUGINS`, and `/plugins anything` are all denied to an ordinary player;
- `guardian.protection.command.bypass` permits execution-policy bypass but does not automatically grant namespace or visibility bypass;
- `guardian.protection.bypass` bypasses all Protection policy surfaces;
- a legacy eZProtector permission by itself has no effect.

## 6. Visibility and suggestions

Using a test account without bypass permissions:

### DENYLIST

Configure `plugins` as a hidden visibility root and verify:

- `plugins` is absent from the client's advertised command roots;
- manually typing `/plugins ` does not reveal downstream argument suggestions;
- execution remains governed separately by `protection.execution`.

### ALLOWLIST

Switch the visibility policy to `ALLOWLIST` with a very small safe set and verify only those Guardian-permitted roots remain advertised, still subject to the commands Paper would ordinarily expose to the player.

### Bypasses

Verify:

- `guardian.protection.visibility.bypass` restores both the hidden root and its downstream suggestions;
- `guardian.protection.visibility.bypass.plugins` restores both for `plugins` only;
- for `?`, the dynamic node is `guardian.protection.visibility.bypass._3f`;
- a visibility bypass alone does not bypass execution denial.

## 7. Namespaced command policy

Exercise both modes with harmless aliases available on the test server:

- `DENYLIST`: a listed alias such as `bukkit:plugins` is denied while an unlisted legitimate namespaced root remains unaffected;
- `ALLOWLIST`: a deliberately listed legitimate alias is permitted while an unlisted namespaced root is denied;
- `guardian.protection.namespace.bypass` affects namespace policy only.

Also verify that ordinary unnamespaced plugin commands continue to work unless independently blocked by execution policy.

## 8. Notifications

With `protection.notifications.enabled: true`:

- a staff account holding only `guardian.protection.notify` receives a denial notice and is still subject to Protection rules when it runs a blocked command itself;
- an account holding a bypass but not `guardian.protection.notify` receives no extra notification authority;
- denial text and notification text come from the locale file.

## 9. Reload/refresh implementation gate

The Phase 1B host contains a validated atomic reload/reconciliation seam for the later administrative command surface. Its behavior must remain:

- invalid Protection candidate → exception, prior immutable snapshot stays active, candidate file untouched;
- changed visibility policy → Protection swaps to the new validated engine and calls supported `Player.updateCommands()` for online players;
- disabling Protection → listeners unregister and online command trees are resent so previously hidden roots can return;
- enabling Protection → listeners register before online command trees are resent.

The public `/guardian reload` command is intentionally not pulled forward from the later operations phase merely to test Phase 1B. Until that administrative surface lands, the automated tests/source guards cover this activation seam; ordinary operator configuration changes are exercised across a controlled restart.

## 10. Phase 1A regressions

After Protection testing, repeat the focused live checks already recorded in `PHASE_1A_VERIFICATION.md`:

- standalone Guardian-Paper Admission path;
- Velocity-authoritative Guardian + Geyser/Floodgate path used by BadWolfMC.

No Protection rule should affect configuration-stage Admission, Cerberus, proxy assertions, or Bedrock classification.

## Completion record

Record the exact JAR/build identifier, test totals, and operator-observed PASS/FAIL results here when the live gate is performed. Do not mark Phase 1B complete solely from source review or a patch applying cleanly.
