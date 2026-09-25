package com.badwolfmc.guardian.protection;

import java.util.Objects;
import java.util.Optional;

/** Platform-neutral Protection evaluator used by execution, tree, and suggestion adapters. */
public final class ProtectionEngine {
    private final ProtectionPolicy policy;
    private final ProtectionBypassResolver bypassResolver;

    public ProtectionEngine(ProtectionPolicy policy) {
        this(policy, new ProtectionBypassResolver());
    }

    ProtectionEngine(ProtectionPolicy policy, ProtectionBypassResolver bypassResolver) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.bypassResolver = Objects.requireNonNull(bypassResolver, "bypassResolver");
    }

    public ProtectionPolicy policy() {
        return policy;
    }

    public Optional<ProtectionDecision> evaluateExecution(
        String rawCommand,
        ProtectionPermissionView permissions
    ) {
        Optional<CommandRoot> normalized = CommandRootNormalizer.tryNormalize(rawCommand);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        CommandRoot root = normalized.get();
        if (root.namespaced()) {
            ProtectionDecision namespaceDecision = evaluateRule(
                policy.namespaces(), root, permissions, ProtectionReason.NAMESPACE_DENIED);
            if (namespaceDecision.denied()) {
                return Optional.of(namespaceDecision);
            }
        }

        return Optional.of(evaluateRule(
            policy.execution(), root, permissions, ProtectionReason.EXECUTION_DENIED));
    }

    /**
     * Shared command-tree and downstream-suggestion visibility decision.
     * Callers MUST use this same method for both surfaces.
     */
    public Optional<ProtectionDecision> evaluateVisibility(
        String rawRootOrBuffer,
        ProtectionPermissionView permissions
    ) {
        Optional<CommandRoot> normalized = CommandRootNormalizer.tryNormalize(rawRootOrBuffer);
        return normalized.map(root -> evaluateRule(
            policy.visibility(), root, permissions, ProtectionReason.VISIBILITY_HIDDEN));
    }

    private ProtectionDecision evaluateRule(
        ProtectionRule rule,
        CommandRoot root,
        ProtectionPermissionView permissions,
        ProtectionReason denyReason
    ) {
        if (!rule.denies(root)) {
            return decision(ProtectionOutcome.ALLOW, ProtectionReason.POLICY_ALLOWED, rule, root, Optional.empty());
        }

        ProtectionBypass bypass = bypassResolver.resolve(rule.surface(), root, policy, permissions);
        if (bypass.granted()) {
            return decision(
                ProtectionOutcome.ALLOW,
                bypassReason(rule.surface(), bypass.kind()),
                rule,
                root,
                bypass.permission());
        }

        return decision(ProtectionOutcome.DENY, denyReason, rule, root, Optional.empty());
    }

    private static ProtectionReason bypassReason(
        ProtectionSurface surface,
        ProtectionBypassKind kind
    ) {
        if (kind == ProtectionBypassKind.GLOBAL) {
            return ProtectionReason.GLOBAL_BYPASS;
        }
        if (kind == ProtectionBypassKind.COMMAND_VISIBILITY) {
            return ProtectionReason.COMMAND_VISIBILITY_BYPASS;
        }
        return switch (surface) {
            case COMMAND_EXECUTION -> ProtectionReason.COMMAND_BYPASS;
            case NAMESPACED_COMMAND -> ProtectionReason.NAMESPACE_BYPASS;
            case COMMAND_VISIBILITY -> ProtectionReason.VISIBILITY_BYPASS;
        };
    }

    private static ProtectionDecision decision(
        ProtectionOutcome outcome,
        ProtectionReason reason,
        ProtectionRule rule,
        CommandRoot root,
        Optional<String> bypassPermission
    ) {
        return new ProtectionDecision(outcome, reason, rule.surface(), root, rule, bypassPermission);
    }
}
