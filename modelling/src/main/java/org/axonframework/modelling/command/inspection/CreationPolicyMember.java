package org.axonframework.modelling.command.inspection;

import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.command.AggregateCreationPolicy;

/**
 * Interface specifying a message handler containing a creation policy definition.
 *
 * @author Marc Gathier
 * @since 4.3
 */
public interface CreationPolicyMember<T> extends MessageHandlingMember<T> {

    AggregateCreationPolicy creationPolicy();
}
