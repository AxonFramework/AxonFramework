package org.axonframework.messaging.annotation;

import org.axonframework.common.Priority;
import org.axonframework.messaging.Message;

/**
 * An extension of the AbstractAnnotatedParameterResolverFactory that accepts
 * parameters of a {@link String} type that are annotated with the {@link MessageIdentifier}
 * annotation and assigns the identifier of the Message.
 */
@Priority(Priority.HIGH)
public final class MessageIdentifierParameterResolverFactory extends AbstractAnnotatedParameterResolverFactory<MessageIdentifier, String> {

    private final ParameterResolver<String> resolver;

    /**
     * Initialize a {@link ParameterResolverFactory} for {@link MessageIdentifier}
     * annotated parameters
     */
    public MessageIdentifierParameterResolverFactory() {
        super(MessageIdentifier.class, String.class);
        resolver = new MessageIdentifierParameterResolver();
    }

    @Override
    protected ParameterResolver<String> getResolver() {
        return resolver;
    }

    /**
     * ParameterResolver to resolve MessageIdentifier parameters
     */
    static class MessageIdentifierParameterResolver implements ParameterResolver<String> {

        @Override
        public String resolveParameterValue(Message message) {
            return message.getIdentifier();
        }

        @Override
        public boolean matches(Message message) {
            return true;
        }

    }

}
