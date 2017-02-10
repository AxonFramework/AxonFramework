package org.axonframework.spring.messaging;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.NumberUtils;

/**
 * An {@link EventMessageConverter} that will convert an Axon event message into a Spring message by:
 * - Copying axon event payload into Spring message payload
 * - Copying axon event metadata into Spring message headers
 * - Adding axon event message specific attributes - that are not part of axon metadata - to the Spring message Headers.
 *   Among those specific attributes are {@link DomainEventMessage} specific properties.
 *
 * @author Reda.Housni-Alaoui
 * @since 3.1
 */
public class DefaultEventMessageConverter implements EventMessageConverter {

	private static final String AXON_MESSAGE_PREFIX = "axon-message-";
	private static final String MESSAGE_ID = AXON_MESSAGE_PREFIX + "id";
	private static final String AGGREGATE_ID = AXON_MESSAGE_PREFIX + "aggregate-id";
	private static final String AGGREGATE_SEQ = AXON_MESSAGE_PREFIX + "aggregate-seq";
	private static final String AGGREGATE_TYPE = AXON_MESSAGE_PREFIX + "aggregate-type";

	@Override
	public <T> Message<T> convertToOutboundMessage(EventMessage<T> event) {
		Map<String, Object> headers = new HashMap<>();
		event.getMetaData().forEach(headers::put);
		headers.put(MESSAGE_ID, event.getIdentifier());
		if (event instanceof DomainEventMessage) {
			headers.put(AGGREGATE_ID, ((DomainEventMessage) event).getAggregateIdentifier());
			headers.put(AGGREGATE_SEQ, ((DomainEventMessage) event).getSequenceNumber());
			headers.put(AGGREGATE_TYPE, ((DomainEventMessage) event).getType());
		}
		return new GenericMessage<>(event.getPayload(), new SettableTimestampMessageHeaders(headers, event.getTimestamp().toEpochMilli()));
	}

	@Override
	public <T> EventMessage<T> convertFromInboundMessage(Message<T> message) {
		MessageHeaders headers = message.getHeaders();
		Map<String, ?> metaData = headers.entrySet()
				.stream()
				.filter(entry -> !entry.getKey().startsWith(AXON_MESSAGE_PREFIX))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

		String messageId = Objects.toString(headers.get(MESSAGE_ID));
		Long timestamp = headers.getTimestamp();

		org.axonframework.messaging.GenericMessage<T> genericMessage
				= new org.axonframework.messaging.GenericMessage<>(messageId, message.getPayload(), metaData);
		if (headers.containsKey(AGGREGATE_ID)) {
			return new GenericDomainEventMessage<>(Objects.toString(headers.get(AGGREGATE_TYPE)),
					Objects.toString(headers.get(AGGREGATE_ID)),
					NumberUtils.convertNumberToTargetClass(headers.get(AGGREGATE_SEQ, Number.class), Long.class),
					genericMessage, () -> Instant.ofEpochMilli(timestamp));
		} else {
			return new GenericEventMessage<>(genericMessage, () -> Instant.ofEpochMilli(timestamp));
		}
	}

	private static class SettableTimestampMessageHeaders extends MessageHeaders {
		protected SettableTimestampMessageHeaders(Map<String, Object> headers, Long timestamp) {
			super(headers, null, timestamp);
		}
	}
}
