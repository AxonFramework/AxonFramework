package org.axonframework.unitofwork;

/**
 * The default implementation of the RollbackAttribute interface. It will return true on every throwable because that was the behaviour unit now.
 *
 * @author Martin Tilma
 * @since 1.1
 */
public class DefaultRollbackAttribute implements RollbackAttribute {

    @Override
    public boolean rollBackOn(Throwable throwable) {
        return true;
    }
}
