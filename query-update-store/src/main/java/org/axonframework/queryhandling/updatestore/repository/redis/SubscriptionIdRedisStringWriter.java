package org.axonframework.queryhandling.updatestore.repository.redis;

import org.axonframework.queryhandling.SubscriptionId;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

@Component
@WritingConverter
public class SubscriptionIdRedisStringWriter implements
        CustomRedisConverter<SubscriptionId, String> {

    @Override
    public String convert(SubscriptionId source) {
        return String.format("%s:%s",
                source.getNodeId(),
                source.getQueryPayloadHash()
        );
    }
}
