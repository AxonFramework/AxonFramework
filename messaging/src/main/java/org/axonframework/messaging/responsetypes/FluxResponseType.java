package org.axonframework.messaging.responsetypes;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * A {@link ResponseType} implementation that will match with query
 * handlers which return a Flux stream of the expected response type. If matching succeeds, the
 * {@link ResponseType#convert(Object)} function will be called, which will cast the query handler it's response to
 * {@code R}.
 *
 * @param <R> The response type which will be matched against and converted to
 * @author Stefan Dragisic
 * @author Milan Savic
 * @since 4.6.0
 */
public class FluxResponseType<R> extends AbstractResponseType<Flux<R>> {

    /**
     * Instantiate a {@link FluxResponseType} with the given
     * {@code expectedResponseType} as the type to be matched against and to which the query response should be
     * converted to.
     *
     * @param expectedResponseType the response type which is expected to be matched against and returned
     */
    public FluxResponseType(Class<?> expectedResponseType) {
        super(expectedResponseType);
    }

    /**
     * Match the query handler its response {@link java.lang.reflect.Type} with this implementation its responseType
     * {@code R}.
     * Will return true if the expected type is assignable to the response type, taking generic types into account.
     *
     * @param responseType the response {@link java.lang.reflect.Type} of the query handler which is matched against
     * @return true if the response type is assignable to the expected type, taking generic types into account
     */
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
