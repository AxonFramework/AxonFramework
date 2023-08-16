package org.axonframework.messaging;

public class UnitOfWorkFactory implements ProcessFactory {

    @Override
    public ProcessingLifecycle create() {
        return new UnitOfWork("uow");
    }
}
