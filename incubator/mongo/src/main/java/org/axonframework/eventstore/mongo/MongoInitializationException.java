/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventstore.mongo;

/**
 * <p>Exception used to indicate the configuration of the MongoDB connection has errors</p>.
 *
 * @author Jettro Coenradie
 * @since 0.7
 */
public class MongoInitializationException extends RuntimeException {

    private static final long serialVersionUID = -1099192821808240472L;

    /**
     * Constructor accepting a custom message.
     *
     * @param message String describing the exception
     */
    public MongoInitializationException(String message) {
        super(message);
    }

    /**
     * Constructor excepting a custom message and the original exception.
     *
     * @param message String describing the exception
     * @param cause   Original exception
     */
    public MongoInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
