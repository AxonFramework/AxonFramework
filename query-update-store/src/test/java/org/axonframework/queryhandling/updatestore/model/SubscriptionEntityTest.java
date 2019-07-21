package org.axonframework.queryhandling.updatestore.model;

import demo.DemoQuery;
import demo.DemoQueryResult;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubscriptionEntityTest {

    private final XStreamSerializer serializer = XStreamSerializer.defaultSerializer();

    @Test
    public void testSerializationLoop() {
        // arrange
        DemoQuery query = new DemoQuery("mockedAggId");
        ResponseType<DemoQueryResult> responseType = ResponseTypes.instanceOf(DemoQueryResult.class);
        // act
        SubscriptionEntity<DemoQuery, DemoQueryResult, DemoQueryResult> entity =
                new SubscriptionEntity<>(
                        "mockedNodeId",
                        query,
                        responseType,
                        responseType,
                        serializer
                );

        SubscriptionQueryMessage<DemoQuery, DemoQueryResult, DemoQueryResult> message =
                entity.asSubscriptionQueryMessage(serializer);

        // assert
        assertEquals("mockedNodeId", message.getQueryName());
        assertEquals(query, message.getPayload());
        assertTrue(message.getResponseType().matches(responseType.getExpectedResponseType()));
        assertTrue(message.getUpdateResponseType().matches(responseType.getExpectedResponseType()));
    }
}
