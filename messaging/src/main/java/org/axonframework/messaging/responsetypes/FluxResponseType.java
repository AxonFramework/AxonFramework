package org.axonframework.messaging.responsetypes;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

// TODO: 10/21/21 javadoc
// TODO: 10/21/21 check compatibility with non-streaming queries
// TODO: 10/21/21 check compatibility when project reactor is not on classpath
public class FluxResponseType<R> extends AbstractResponseType<Flux<R>> {

    // TODO: 10/21/21 javadoc
    public FluxResponseType(Class<?> expectedResponseType) {
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

    @Override
    public Flux<R> convert(Object queryResponse) {
        if (queryResponse == null) {
            return Flux.empty();
        } else if (Flux.class.isAssignableFrom(queryResponse.getClass())) {
            return (Flux<R>) queryResponse;
        } else if (Iterable.class.isAssignableFrom(queryResponse.getClass())) {
            return Flux.fromIterable((Iterable) queryResponse);
        } else if (Stream.class.isAssignableFrom(queryResponse.getClass())) {
            return Flux.fromStream((Stream) queryResponse);
        } else if (Mono.class.isAssignableFrom(queryResponse.getClass())) {
            return Flux.from((Mono) queryResponse);
        } else if (CompletableFuture.class.isAssignableFrom(queryResponse.getClass())) {
            return Flux.from(Mono.fromCompletionStage((CompletableFuture) queryResponse));
        } else if (Optional.class.isAssignableFrom(queryResponse.getClass())) {
            return (Flux<R>) ((Optional) queryResponse).map(Flux::just).orElse(Flux.<R>empty());
        }
        return Flux.just((R) queryResponse);
    }

    @Override
    public Optional<Flux<R>> convertExceptional(Throwable e) {
        return Optional.of(Flux.error(e));
    }
}
