package org.axonframework.queryhandling.updatestore.repository.redis;

import org.axonframework.queryhandling.SubscriptionId;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@WritingConverter
public class SubscriptionIdRedisBytesWriter
        implements CustomRedisConverter<SubscriptionId, byte[]> {

    private final SubscriptionIdRedisStringWriter stringConverter;

    public SubscriptionIdRedisBytesWriter(SubscriptionIdRedisStringWriter stringConverter) {
        this.stringConverter = stringConverter;
    }

    @Override
    public byte[] convert(SubscriptionId source) {
        return stringConverter.convert(source)
                .getBytes(StandardCharsets.UTF_8);
    }
}
