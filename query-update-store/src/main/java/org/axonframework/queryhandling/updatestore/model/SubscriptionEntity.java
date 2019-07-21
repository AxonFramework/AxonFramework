package org.axonframework.queryhandling.updatestore.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericSubscriptionQueryMessage;
import org.axonframework.queryhandling.SubscriptionId;
import org.axonframework.queryhandling.SubscriptionQueryMessage;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import javax.persistence.*;
import java.time.Instant;

import static org.axonframework.queryhandling.serialize.SerializationUtil.serialize;

@Entity
@RedisHash("subscription")
@Data
@NoArgsConstructor
public class SubscriptionEntity<Q, I, U> {

    @Id
    @EmbeddedId
    private SubscriptionId id;

    @Lob
    @Column(length = 8 * 1024)
    private byte[] queryPayload;
    private String queryPayloadType;
    private String queryPayloadRevision;

    private String queryInitialResponseType;
    private String queryUpdateResponseType;

    private Instant creationTime = Instant.now();

    @TimeToLive
    private int timeoutSeconds = 1;

    public SubscriptionEntity(String nodeId,
                              Q queryPayload,
                              ResponseType<I> queryInitialResponseType,
                              ResponseType<U> queryUpdateResponseType,
                              Serializer serializer) {
        this.id = new SubscriptionId(nodeId, queryPayload, serializer);

        SerializedObject<byte[]> serializedPayload = serialize(queryPayload, serializer);
        this.queryPayload = serializedPayload.getData();
        this.queryPayloadType = serializedPayload.getType().getName();
        this.queryPayloadRevision = serializedPayload.getType().getRevision();

        this.queryInitialResponseType = queryInitialResponseType.responseMessagePayloadType().getName();
        this.queryUpdateResponseType = queryUpdateResponseType.responseMessagePayloadType().getName();
    }

    @SuppressWarnings("unchecked")
    @Transient
    public SubscriptionQueryMessage<Q, I, U> asSubscriptionQueryMessage(Serializer serializer) {
        try {
            Q payload = serializer.deserialize(
                    new SimpleSerializedObject<>(
                            queryPayload,
                            byte[].class,
                            queryPayloadType,
                            queryPayloadRevision)
            );
            return new GenericSubscriptionQueryMessage<>(
                    payload,
                    id.getNodeId(),
                    ResponseTypes.instanceOf((Class<I>) Class.forName(queryInitialResponseType)),
                    ResponseTypes.instanceOf((Class<U>) Class.forName(queryUpdateResponseType))
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
