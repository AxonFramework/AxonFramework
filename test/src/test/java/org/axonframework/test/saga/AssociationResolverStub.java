package org.axonframework.test.saga;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.saga.AssociationResolver;
import org.axonframework.modelling.saga.PayloadAssociationResolver;
import jakarta.annotation.Nonnull;

import static org.junit.Assert.*;

public class AssociationResolverStub implements AssociationResolver {

    private final PayloadAssociationResolver defaultResolver = new PayloadAssociationResolver();

    @Override
    public <T> void validate(@Nonnull String associationPropertyName, @Nonnull MessageHandlingMember<T> handler) {
        defaultResolver.validate(associationPropertyName, handler);
    }

    @Override
    public <T> Object resolve(@Nonnull String associationPropertyName, @Nonnull EventMessage<?> message,
                              @Nonnull MessageHandlingMember<T> handler) {


        if (!DomainEventMessage.class.isAssignableFrom(message.getClass())) {
            fail("message is not assignable from DomainEventMessage");
        }
        return defaultResolver.resolve(associationPropertyName, message, handler);
    }
}
