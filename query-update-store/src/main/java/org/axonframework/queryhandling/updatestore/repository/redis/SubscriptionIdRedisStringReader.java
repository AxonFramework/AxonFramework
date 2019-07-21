package org.axonframework.queryhandling.updatestore.repository.redis;

import org.axonframework.queryhandling.SubscriptionId;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

import java.util.StringTokenizer;

@Component
@ReadingConverter
public class SubscriptionIdRedisStringReader implements
        CustomRedisConverter<String, SubscriptionId> {

    @Override
    public SubscriptionId convert(String source) {
        StringTokenizer tokenizer = new StringTokenizer(source, ":");
        String nodeId = tokenizer.nextToken();
        String payloadHash = tokenizer.nextToken();
        return new SubscriptionId(nodeId, payloadHash);
    }
}
