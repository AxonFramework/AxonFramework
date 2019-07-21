package org.axonframework.queryhandling.updatestore.repository.redis;

import org.springframework.core.convert.converter.Converter;

/**
 * Marker interface for
 * {@link org.axonframework.queryhandling.config.DistributedQueryBusRedisAutoConfiguration#customRedisConvertersList}
 *
 * @param <S>
 * @param <T>
 */
public interface CustomRedisConverter<S, T> extends Converter<S, T> {
}
