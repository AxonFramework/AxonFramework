package org.axonframework.messaging.unitofwork;

import java.util.concurrent.CompletableFuture;

/**
 * Test class validating whether the {@link AsyncUnitOfWork} complies with the expectations of a {@link ProcessingLifecycle} implementation.
 *
 * @author Gerard Klijs
 * @author Milan Savić
 * @author Sara Pellegrini
 * @author Steven van Beelen
 */
class AsyncUnitOfWorkTest extends ProcessingLifecycleTest<AsyncUnitOfWork> {

    @Override
    AsyncUnitOfWork testSubject() {
        return new AsyncUnitOfWork();
    }

    @Override
    CompletableFuture<?> execute(AsyncUnitOfWork testSubject) {
        return testSubject.execute();
    }
}
