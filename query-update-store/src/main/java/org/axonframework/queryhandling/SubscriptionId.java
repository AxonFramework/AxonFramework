package org.axonframework.queryhandling;

import com.google.common.annotations.VisibleForTesting;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.queryhandling.serialize.SerializationUtil;
import org.axonframework.serialization.Serializer;
import org.springframework.util.DigestUtils;

import javax.persistence.Embeddable;
import java.io.Serializable;

@Data
@NoArgsConstructor
@Embeddable
public class SubscriptionId implements Serializable {

    private String nodeId;

    private String queryPayloadHash;

    public SubscriptionId(String nodeId, Object query, Serializer serializer) {
        this(nodeId, SerializationUtil.serialize(query, serializer).getData());
    }

    @VisibleForTesting
    public SubscriptionId(String nodeId, String payloadHash) {
        this.nodeId = nodeId;

        this.queryPayloadHash = payloadHash;
    }

    private SubscriptionId(String nodeId, byte[] payload) {
        this.nodeId = nodeId;

        this.queryPayloadHash = hash(payload);
    }

    private String hash(byte[] payload) {
        return DigestUtils.md5DigestAsHex(payload);
    }
}
