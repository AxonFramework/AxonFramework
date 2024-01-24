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

package org.axonframework.messaging;

import org.axonframework.messaging.unitofwork.ProcessingContext;

import java.util.concurrent.CompletableFuture;

/**
 * The interceptor chain manages the flow of a message through a chain of interceptors and ultimately to the message
 * handler. Interceptors may continue processing via this chain by calling the {@link #proceedSync()} method.
 * Alternatively, they can block processing by returning without calling either of these methods.
 *
 * @author Allard Buijze
 * @since 0.5
 */
@FunctionalInterface
public interface InterceptorChain {

    /**
     * Signals the Interceptor Chain to continue processing the message.
     *
     * @return The return value of the message processing
     * @throws Exception any exceptions thrown by interceptors or the message handler
     */
    Object proceedSync() throws Exception;

    default CompletableFuture<?> proceed(Message<?> message, ProcessingContext processingContext) {
        try {
            return CompletableFuture.completedFuture(proceedSync());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

}
