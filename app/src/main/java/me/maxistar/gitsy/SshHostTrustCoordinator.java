package me.maxistar.gitsy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Keeps non-secret pending trust decisions stable across repeated callbacks and Activity recreation. */
public final class SshHostTrustCoordinator {
    private final TrustedHostRepository repository;
    private final Map<String, PendingDecision> byOperation = new HashMap<>();
    private final Map<String, PendingDecision> byToken = new HashMap<>();

    public SshHostTrustCoordinator(TrustedHostRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public synchronized void verify(String operationId, SshHostIdentity candidate)
            throws Exception {
        TrustedHostMatch match = repository.check(candidate);
        if (match == TrustedHostMatch.MATCH) return;
        if (match == TrustedHostMatch.CHANGED) throw new SshHostKeyChangedException(candidate);

        PendingDecision pending = byOperation.get(operationId);
        if (pending == null) {
            pending = new PendingDecision(operationId, UUID.randomUUID().toString(), candidate);
            byOperation.put(operationId, pending);
            byToken.put(pending.token, pending);
        }
        throw new SshHostTrustRequiredException(new SshHostTrustRequest(pending.token, pending.identity));
    }

    public synchronized SshHostTrustRequest pending(String token) {
        PendingDecision pending = byToken.get(token);
        return pending == null ? null : new SshHostTrustRequest(pending.token, pending.identity);
    }

    /** Returns true exactly once when the caller should retry the stopped operation. */
    public synchronized boolean accept(String token) throws Exception {
        PendingDecision pending = byToken.get(token);
        if (pending == null) return false;
        repository.trustUnknown(pending.identity);
        remove(token);
        return true;
    }

    public synchronized boolean decline(String token) {
        return remove(token) != null;
    }

    private PendingDecision remove(String token) {
        PendingDecision pending = byToken.remove(token);
        if (pending != null) byOperation.remove(pending.operationId);
        return pending;
    }

    private static final class PendingDecision {
        final String operationId;
        final String token;
        final SshHostIdentity identity;
        PendingDecision(String operationId, String token, SshHostIdentity identity) {
            this.operationId = operationId;
            this.token = token;
            this.identity = identity;
        }
    }
}
