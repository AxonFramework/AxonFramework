package org.axonframework.spring.messaging;


import static org.junit.Assert.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.GenericDomainEventMessage;
import org.junit.Test;

/**
 * Created on 04/02/17.
 *
 * @author Reda.Housni-Alaoui
 */
public class DefaultEventMessageConverterTest {

	private EventMessageConverter eventMessageConverter = new DefaultEventMessageConverter();

	@Test
	public void given_generic_event_message_when_converting_twice_then_resulting_event_should_be_the_same(){
		Instant instant = Instant.EPOCH;
		String id = UUID.randomUUID().toString();

		Map<String, Object> metaData = new HashMap<>();
		metaData.put("number", 100);
		metaData.put("string", "world");

		EventPayload payload = new EventPayload("hello");

		EventMessage<EventPayload> axonMessage = new GenericEventMessage<>(id, payload, metaData, instant);

		EventMessage<EventPayload> convertedAxonMessage = eventMessageConverter.convertFromInboundMessage(eventMessageConverter.convertToOutboundMessage(axonMessage));

		assertEquals(instant, convertedAxonMessage.getTimestamp());
		assertEquals(100, convertedAxonMessage.getMetaData().get("number"));
		assertEquals("world", convertedAxonMessage.getMetaData().get("string"));
		assertEquals("hello", convertedAxonMessage.getPayload().name);
		assertEquals(id, convertedAxonMessage.getIdentifier());
	}

	@Test
	public void given_domain_event_message_when_converting_twice_then_resulting_event_should_be_the_same(){
		Instant instant = Instant.EPOCH;
		String id = UUID.randomUUID().toString();
		String aggId = UUID.randomUUID().toString();

		Map<String, Object> metaData = new HashMap<>();
		metaData.put("number", 100);
		metaData.put("string", "world");

		EventPayload payload = new EventPayload("hello");

		EventMessage<EventPayload> axonMessage = new GenericDomainEventMessage<>("foo", aggId, 1, payload, metaData, id, instant);
		EventMessage<EventPayload> convertedAxonMessage = eventMessageConverter.convertFromInboundMessage(eventMessageConverter.convertToOutboundMessage(axonMessage));

		assertTrue(convertedAxonMessage instanceof DomainEventMessage);

		DomainEventMessage<EventPayload> convertDomainMessage = (DomainEventMessage<EventPayload>) convertedAxonMessage;
		assertEquals(instant, convertDomainMessage.getTimestamp());
		assertEquals(100, convertDomainMessage.getMetaData().get("number"));
		assertEquals("world", convertDomainMessage.getMetaData().get("string"));
		assertEquals("hello", convertDomainMessage.getPayload().name);
		assertEquals(id, convertDomainMessage.getIdentifier());
		assertEquals("foo", convertDomainMessage.getType());
		assertEquals(aggId, convertDomainMessage.getAggregateIdentifier());
		assertEquals(1, convertDomainMessage.getSequenceNumber());
	}

	private class EventPayload{
		private final String name;

		EventPayload(String name){
			this.name = name;
		}
	}

}