/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.queryhandling;

import org.axonframework.messaging.MessageHandler;

import javax.annotation.Nonnull;

/**
 * Interface describing a mechanism for the QueryMessage components to report errors.
 * <p>
 * Note that this handler is not used for point-to-point queries. In that case, exceptions are reported directly to the
 * invoker. Query Bus implementation may choose to implement point-to-point queries as scatter-gather, and only
 * reporting the first answer returned. In that case, this handler is invoked, and any propagated exceptions may or may
 * not be reported to the sender of the query.
 *
 * @author Allard Buijze
 * @author Marc Gathier
 * @since 3.1
 */
public interface QueryInvocationErrorHandler {

    /**
     * Invoked when an error occurred while invoking a message handler in a scatter-gather query. Implementations may
     * throw an exception to propagate the error to the caller. In that case, the QueryBus implementation will receive
     * the exception, and may choose to propagate the error to the sender of the query.
     * <p>
     * Note that this handler is not used for point-to-point queries. In that case, exceptions are reported directly to
     * the invoker. Query Bus implementation may choose to implement point-to-point queries as scatter-gather, and only
     * reporting the first answer returned. In that case, this method is invoked, and any propagated exceptions may or
     * may not be reported to the sender of the query.
     *
     * @param error          The error that occurred
     * @param queryMessage   The message causing the exception in the handler
     * @param messageHandler The handler that reported the exception
     */
    void onError(@Nonnull Throwable error, @Nonnull QueryMessage<?, ?> queryMessage,
                 @Nonnull MessageHandler messageHandler);
}
