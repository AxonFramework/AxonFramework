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
 * Exception created to log a warning when a message size exceeds a certain threshold.
 * This means that the application has tried to send a message to Axon Server that is almost too large.
 * This can be a command, query, response, or any other message.
 * <p>
 * This exception is never thrown, but only used to log a warning that includes the stack trace for better analysis.
 *
 * @author Mitchell Herrijgers
 * @see GrpcMessageSizeInterceptor
 * @since 4.11.0
 */
public class GrpcMessageSizeWarningThresholdReachedException extends RuntimeException {
    /**
     * Creates a new instance of the exception indicating that the message size has exceeded the threshold.
     */
    public GrpcMessageSizeWarningThresholdReachedException() {
        super("Message size exceeds the warning threshold. This message will be accepted, but it is recommended to reduce the message size or increase the limits.");
    }
}
