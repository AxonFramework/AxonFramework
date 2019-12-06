package org.axonframework.modelling.command.inspection;

import org.axonframework.messaging.annotation.HandlerEnhancerDefinition;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.CreationPolicy;

import java.util.Map;

/**
 * Implementation of {@link HandlerEnhancerDefinition} used for {@link CreationPolicy} annotated methods.
 *
 * @author Marc Gathier
 * @since 4.3
 */
public class MethodCreationPolicyDefinition implements HandlerEnhancerDefinition {

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(MessageHandlingMember<T> original) {
        return original.annotationAttributes(CreationPolicy.class)
                       .map(attr -> (MessageHandlingMember<T>) new MethodCreationPolicyHandlingMember<>(
                               original, attr))
                       .orElse(original);
    }

    private static class MethodCreationPolicyHandlingMember<T> extends WrappedMessageHandlingMember<T>
            implements CreationPolicyMember<T> {

        private final AggregateCreationPolicy creationPolicy;

        private MethodCreationPolicyHandlingMember(
                MessageHandlingMember<T> delegate, Map<String, Object> attr) {
            super(delegate);
            creationPolicy = (AggregateCreationPolicy) attr.get("creationPolicy");
        }

        @Override
        public AggregateCreationPolicy creationPolicy() {
            return creationPolicy;
        }
    }
}
