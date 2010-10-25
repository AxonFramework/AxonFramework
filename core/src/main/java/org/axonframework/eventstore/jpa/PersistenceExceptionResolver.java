package org.axonframework.eventstore.jpa;

import javax.persistence.PersistenceException;

/**
 *  The PersistenceExceptionResolver is used to find out if an exception is caused by  duplicate keys
 *
 * @author Martin Tilma
 * @since 0.7
 *
 */
public interface PersistenceExceptionResolver {
    
    boolean isDuplicateKey(PersistenceException persistenceException);

}
