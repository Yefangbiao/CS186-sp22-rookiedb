package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.TransactionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * LockUtil is a declarative layer which simplifies multigranularity lock
 * acquisition for the user (you, in the last task of Part 2). Generally
 * speaking, you should use LockUtil for lock acquisition instead of calling
 * LockContext methods directly.
 */
public class LockUtil {
    /**
     * Ensure that the current transaction can perform actions requiring
     * `requestType` on `lockContext`.
     *
     * `requestType` is guaranteed to be one of: S, X, NL.
     *
     * This method should promote/escalate/acquire as needed, but should only
     * grant the least permissive set of locks needed. We recommend that you
     * think about what to do in each of the following cases:
     * - The current lock type can effectively substitute the requested type
     * - The current lock type is IX and the requested lock is S
     * - The current lock type is an intent lock
     * - None of the above: In this case, consider what values the explicit
     *   lock type can be, and think about how ancestor looks will need to be
     *   acquired or changed.
     *
     * You may find it useful to create a helper method that ensures you have
     * the appropriate locks on all ancestors.
     */
    public static void ensureSufficientLockHeld(LockContext lockContext, LockType requestType) {
        // requestType must be S, X, or NL
        assert (requestType == LockType.S || requestType == LockType.X || requestType == LockType.NL);

        // Do nothing if the transaction or lockContext is null
        TransactionContext transaction = TransactionContext.getTransaction();
        if (transaction == null || lockContext == null) return;

        // You may find these variables useful
        LockContext parentContext = lockContext.parentContext();
        LockType effectiveLockType = lockContext.getEffectiveLockType(transaction);
        LockType explicitLockType = lockContext.getExplicitLockType(transaction);

        // TODO(proj4_part2): implement
        // The current lock type can effectively substitute the requested type
        if (LockType.substitutable(effectiveLockType, requestType)) {
            return;
        }
        // The current lock type is IX and the requested lock is S
        if (explicitLockType == LockType.IX && requestType == LockType.S) {
            lockContext.promote(transaction, LockType.SIX);
            return;
        }
        // The current lock type is an intent lock
        if (explicitLockType.isIntent()) {
            lockContext.escalate(transaction);
            return;
        }
        // None of the above: we should consider Pair<explicitLockType, requestType> [{NL,S}, {NL,X}, {S,X}]
        enforcePromote(lockContext, requestType);
        return;
    }

    // TODO(proj4_part2) add any helper methods you want
    private static void enforcePromote(LockContext lockContext, LockType requestType) {
        TransactionContext transaction = TransactionContext.getTransaction();
        LockType explicitLockType = lockContext.getExplicitLockType(transaction);

        LockContext parentContext = lockContext.parentContext();
        List<LockContext> parents = new ArrayList<>();
        while (parentContext != null) {
            LockType parentLockType = parentContext.getExplicitLockType(transaction);
            if (LockType.canBeParentLock(parentLockType, requestType)) {
                break;
            }
            parents.add(0, parentContext);
            parentContext = parentContext.parentContext();
        }

        LockType shouldType = requestType == LockType.S ? LockType.IS : LockType.IX;
        for (LockContext parent : parents) {
            if (parent.getExplicitLockType(transaction) == LockType.NL) {
                parent.acquire(transaction, shouldType);
            } else {
                parent.promote(transaction, shouldType);
            }
        }

        if (explicitLockType == LockType.NL) {
            lockContext.acquire(transaction, requestType);
        } else {
            lockContext.promote(transaction, requestType);
        }
    }
}
