package org.axonframework.unitofwork;

/**
 * The RollbackAttribute defines when a UnitOfWork should be rollback-ed when a Throwable is thrown from the {@link
 * org.axonframework.commandhandling.InterceptorChain#proceed()}  in the SimpleCommandBus.
 *
 * @author Martin Tilma
 * @since 1.1
 */
public interface RollbackAttribute {

    /**
     * Decides based on the throwable if a UnitOfWork should be rollback-ed.
     * @param throwable the Throwable to evaluate
     * @return true if the UnitOfWork should be rollback-ed otherwise false
     */
    boolean rollBackOn(Throwable throwable);
}
