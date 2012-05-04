package org.axonframework.eventhandling.transactionmanagers;

import org.axonframework.eventhandling.RetryPolicy;
import org.axonframework.eventhandling.TransactionManager;
import org.axonframework.eventhandling.TransactionStatus;
import org.axonframework.unitofwork.CurrentUnitOfWork;
import org.axonframework.unitofwork.DefaultUnitOfWork;
import org.axonframework.unitofwork.UnitOfWork;
import org.axonframework.unitofwork.UnitOfWorkListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;

/**
 * Abstract implementation of the {@link TransactionManager} interface that ensures a UnitOfWork is used to contain the
 * transaction. This way, proper locking and unlocking ordering is guaranteed in combination with the underlying
 * transaction.
 *
 * @param <T> The type of transaction status object used by the underlying transaction manager.
 * @author Allard Buijze
 * @since 1.3
 */
public abstract class AbstractTransactionManager<T> implements TransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(AbstractTransactionManager.class);

    @Override
    public void beforeTransaction(TransactionStatus transactionStatus) {
        UnitOfWork uow = DefaultUnitOfWork.startAndGet();
        final T tx = startUnderlyingTransaction(transactionStatus);
        uow.registerListener(new UnitOfWorkListenerAdapter() {
            @Override
            public void afterCommit() {
                commitUnderlyingTransaction(tx);
            }

            @Override
            public void onRollback(Throwable failureCause) {
                rollbackUnderlyingTransaction(tx);
            }
        });
    }

    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    @Override
    public void afterTransaction(TransactionStatus transactionStatus) {
        if (transactionStatus.isSuccessful()) {
            CurrentUnitOfWork.commit();
        } else {
            logger.warn("Found failed transaction: [{}].",
                        transactionStatus.getException().getClass().getSimpleName());
            if (!isTransient(transactionStatus.getException())) {
                logger.error("ERROR! Exception is not transient or recoverable! Committing transaction and "
                                     + "skipping Event processing", transactionStatus.getException());
                transactionStatus.setRetryPolicy(RetryPolicy.SKIP_FAILED_EVENT);
                CurrentUnitOfWork.commit();
            } else {
                logger.warn("Performing rollback on transaction due to recoverable exception: [{}]",
                            transactionStatus.getException().getClass().getSimpleName());
                transactionStatus.setRetryPolicy(RetryPolicy.RETRY_TRANSACTION);
                CurrentUnitOfWork.get().rollback(transactionStatus.getException());
            }
        }
    }

    /**
     * Starts a transaction in the underlying transaction manager. The returned value will be passed as parameter to
     * the {@link #commitUnderlyingTransaction(Object)} or {@link #rollbackUnderlyingTransaction(Object)} method.
     *
     * @param transactionStatus the object describing the transaction
     * @return an object describing the underlying transaction.
     */
    protected abstract T startUnderlyingTransaction(TransactionStatus transactionStatus);

    /**
     * Commits the transaction described by <code>tx</code> in the underlying transaction manager.
     *
     * @param tx The object returned by {@link #startUnderlyingTransaction(org.axonframework.eventhandling.TransactionStatus)}
     */
    protected abstract void commitUnderlyingTransaction(T tx);

    /**
     * Rolls back the transaction described by <code>tx</code> in the underlying transaction manager.
     *
     * @param tx The object returned by {@link #startUnderlyingTransaction(org.axonframework.eventhandling.TransactionStatus)}
     */
    protected abstract void rollbackUnderlyingTransaction(T tx);

    @SuppressWarnings({"SimplifiableIfStatement"})
    private boolean isTransient(Throwable exception) {
        if (exception instanceof SQLTransientException || exception instanceof SQLRecoverableException) {
            return true;
        }
        if (exception.getCause() != null && exception.getCause() != exception) {
            return isTransient(exception.getCause());
        }
        return false;
    }
}
