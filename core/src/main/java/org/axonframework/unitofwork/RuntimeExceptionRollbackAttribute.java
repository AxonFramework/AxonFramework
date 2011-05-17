package org.axonframework.unitofwork;

/**
 * RuntimeExceptionRollbackAttribute returns true if the throwable is a RuntimeException.
 */
public class RuntimeExceptionRollbackAttribute implements RollbackAttribute {

    @Override
    public boolean rollBackOn(Throwable throwable) {
        return throwable instanceof RuntimeException;
    }
}
