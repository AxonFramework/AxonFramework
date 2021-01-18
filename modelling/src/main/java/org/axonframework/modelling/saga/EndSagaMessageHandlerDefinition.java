package org.axonframework.modelling.saga;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;

/**
 * A {@link HandlerEnhancerDefinition} inspecting the existence of the {@link EndSaga} annotation on {@link
 * MessageHandlingMember}s. If present, the given {@code MessageHandlingMember} will be wrapped in a {@link
 * EndSageMessageHandlingMember}.
 *
 * @author Steven van Beelen
 * @since 4.5
 */
public class EndSagaMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {
        return original.hasAnnotation(EndSaga.class) ? new EndSageMessageHandlingMember<>(original) : original;
    }

    /**
     * A {@link WrappedMessageHandlingMember} implementation dedicated towards {@link MessageHandlingMember}s annotated
     * with {@link EndSaga}. After invocation of the {@link #handle(Message, Object)} method, the saga's is ended
     * through the {@link SagaLifecycle#end()} method.
     *
     * @param <T> the entity type wrapped by this {@link MessageHandlingMember}
     */
    public static class EndSageMessageHandlingMember<T> extends WrappedMessageHandlingMember<T> {

        /**
         * Initializes the member using the given {@code delegate}.
         *
         * @param delegate the actual message handling member to delegate to
         */
        protected EndSageMessageHandlingMember(MessageHandlingMember<T> delegate) {
            super(delegate);
        }

        @Override
        public Object handle(Message<?> message, T target) throws Exception {
            try {
                return super.handle(message, target);
            } finally {
                SagaLifecycle.end();
            }
        }
    }
}
