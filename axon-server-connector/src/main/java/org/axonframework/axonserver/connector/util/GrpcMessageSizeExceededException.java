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

package org.axonframework.axonserver.connector.util;

/**
 * Exception thrown when a message exceeds the maximum allowed size. This means that the application has tried
 * to send a message to Axon Server that is too large. This can be a command, query, response, or any other message.
 *
 * @author Mitchell Herrijgers
 * @see GrpcMessageSizeInterceptor
 * @since 4.11.0
 */
public class GrpcMessageSizeExceededException extends RuntimeException {

    /**
     * Creates a new exception that indicates the gRPC message size has been exceeded with the given message.
     * @param message the message to include in the exception
     */
    public GrpcMessageSizeExceededException(String message) {
        super(message);
    }
}
