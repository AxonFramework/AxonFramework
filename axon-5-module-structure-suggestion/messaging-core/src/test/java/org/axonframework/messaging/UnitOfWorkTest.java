package org.axonframework.messaging;

import java.util.concurrent.CompletableFuture;

class UnitOfWorkTest {

    public void doTest() {
        UnitOfWork unitOfWork = new UnitOfWork();
        unitOfWork.on(ProcessingLifecycle.Phase.COMMIT, context -> {
            return CompletableFuture.supplyAsync(() -> {
                // Event handler invocations
                return null;
            });
        });
//        unitOfWork.execute(context -> {
//            return CompletableFuture.supplyAsync(() -> {
//                // Event handler invocations
//                return null;
//            });
//        });
    }
}