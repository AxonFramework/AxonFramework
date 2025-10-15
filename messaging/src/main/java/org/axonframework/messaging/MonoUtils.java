/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.messaging;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.MessageStream.Single;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Utility methods to work with Project Reactor's {@link Mono monos}.
 *
 * @author John Hendrikx
 * @since 5.0.0
 */
public abstract class MonoUtils {

    private MonoUtils() {
    }

    /**
     * Create a stream that returns a single {@link Entry entry} wrapping the {@link Message} from the given
     * {@code mono}, once the given {@code mono} completes.
     * <p>
     * The stream will contain at most a single entry. It may also contain no entries if the mono completes empty. The
     * stream will complete with an exception when the given {@code mono} completes exceptionally.
     *
     * @param mono The {@link Mono} providing the {@link Message} to contain in the stream.
     * @param <M>  The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A stream containing at most one {@link Entry entry} from the given {@code mono}.
     */
    public static <M extends Message> Single<M> asSingle(@Nonnull Mono<M> mono) {
        return MessageStream.fromFuture(mono.toFuture());
    }

    /**
     * Create a stream that returns a single {@link Entry entry} wrapping the {@link Message} from the given
     * {@code mono}, once the given {@code mono} completes.
     * <p>
     * The automatically generated {@code Entry} will have the {@link Context} as given by the {@code contextSupplier}.
     * <p>
     * The stream will contain at most a single entry. It may also contain no entries if the mono completes empty. The
     * stream will complete with an exception when the given {@code mono} completes exceptionally.
     *
     * @param mono            The {@link Mono} providing the {@link Message} to contain in the stream.
     * @param contextSupplier A {@link Function} ingesting the {@link Message} from the given {@code mono} returning the
     *                        {@link Context} to set for the {@link Entry} the {@code Message} is wrapped in.
     * @param <M>             The type of {@link Message} contained in the {@link Entry entries} of this stream.
     * @return A stream containing at most one {@link Entry entry} from the given {@code mono}.
     */
    public static <M extends Message> Single<M> asSingle(@Nonnull Mono<M> mono,
                                                         @Nonnull Function<M, Context> contextSupplier) {
        return MessageStream.fromFuture(mono.toFuture(), contextSupplier);
    }

}
