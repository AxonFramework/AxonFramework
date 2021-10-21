package org.axonframework.messaging.responsetypes;

import reactor.core.publisher.Flux;

import java.lang.reflect.Type;

// TODO: 10/21/21 javadoc
// TODO: 10/21/21 check compatibility with non-streaming queries
// TODO: 10/21/21 check compatibility when project reactor is not on classpath
public class StreamResponseType<R> extends AbstractResponseType<Flux<R>>{

    // TODO: 10/21/21 javadoc
    protected StreamResponseType(Class<?> expectedResponseType) {
        super(expectedResponseType);
    }

    @Override
    public boolean matches(Type responseType) {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class responseMessagePayloadType() {
        return Flux.class;
    }
}
