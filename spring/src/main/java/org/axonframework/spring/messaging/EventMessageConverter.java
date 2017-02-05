package org.axonframework.spring.messaging;

import org.axonframework.eventhandling.EventMessage;
import org.springframework.messaging.Message;

/**
 * Interface describing a mechanism that converts Spring Messages from an Axon Event Messages and vice versa.
 *
 * @author Reda.Housni-Alaoui
 * @since 3.1
 */
public interface EventMessageConverter {

	/**
	 * Converts Axon {@code event} into Spring message.
	 *
	 * @param event The Axon event to convert
	 * @param <T>   The event payload type
	 * @return The outbound Spring message
	 */
	<T> Message<T> convertToOutboundMessage(EventMessage<T> event);

	/**
	 * Converts a Spring inbound {@code message} into an Axon event Message
	 *
	 * @param message The Spring message to convert
	 * @param <T>     The message payload type
	 * @return The inbound Axon event message
	 */
	<T> EventMessage<T> convertFromInboundMessage(Message<T> message);
}
