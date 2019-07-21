package org.axonframework.queryhandling.updatestore.repository.redis;

import org.axonframework.queryhandling.SubscriptionId;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ReadingConverter
public class SubscriptionIdRedisBytesReader implements
        CustomRedisConverter<byte[], SubscriptionId> {

    private final SubscriptionIdRedisStringReader stringReader;

    public SubscriptionIdRedisBytesReader(SubscriptionIdRedisStringReader stringReader) {
        this.stringReader = stringReader;
    }

    @Override
    public SubscriptionId convert(byte[] source) {
        return stringReader.convert(new String(source, StandardCharsets.UTF_8));
    }
}
