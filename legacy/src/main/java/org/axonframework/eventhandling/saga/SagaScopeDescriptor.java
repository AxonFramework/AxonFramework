package org.axonframework.eventhandling.saga;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Implementation of the {@link org.axonframework.modelling.saga.SagaScopeDescriptor} used to bridge serialized versions
 * of this descriptor when migrating from Axon 3.x to Axon 4.x.
 *
 * @author Steven van Beelen
 * @since 4.2
 * @deprecated in favor of the {@link org.axonframework.modelling.saga.SagaScopeDescriptor}
 */
@Deprecated
public class SagaScopeDescriptor extends org.axonframework.modelling.saga.SagaScopeDescriptor {

    /**
     * Instantiate a SagaScopeDescriptor with the provided {@code type} and {@code identifier}.
     *
     * @param type       A {@link String} describing the type of the Saga
     * @param identifier An {@link Object} denoting the identifier of the Saga
     */
    @JsonCreator
    public SagaScopeDescriptor(@JsonProperty("type") String type, @JsonProperty("identifier") Object identifier) {
        super(type, identifier);
    }
}
