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

package org.axonframework.messaging.deadletter;

import org.axonframework.common.AxonException;

import java.util.Objects;

/**
 * An implementation of {@link Cause} taking in a {@link Throwable}.
 *
 * @author Steven van Beelen
 * @author Mitchel Herrijgers
 * @since 4.6.0
 */
public class ThrowableCause extends AxonException implements Cause {

    /**
     * The default size of {@code 1023} to truncate a {@link Throwable#getMessage()} to, to fit into typical dead-letter
     * storages.
     */
    public static final int TRUNCATED_MESSAGE_SIZE = 1023;

    private final String type;
    private final String message;

    /**
     * Construct a cause based on the given {@code throwable}. Uses the fully qualified class name as the
     * {@link #type() type} and the {@link Throwable#getMessage()} as the {@link #message() message}.
     *
     * @param throwable The throwable to base this cause on.
     */
    public ThrowableCause(Throwable throwable) {
        super(throwable.getMessage(), throwable);
        this.type = throwable.getClass().getName();
        this.message = throwable.getMessage();
    }

    /**
     * Constructs a cause based on the give {@code type} and {@code message}.
     *
     * @param type    The type of this cause.
     * @param message The message of this cause.
     */
    public ThrowableCause(String type, String message) {
        super(message);
        this.type = type;
        this.message = message;
    }

    /**
     * Return the given {@code cause} as a {@link ThrowableCause}.
     * <p>
     * If the given {@code cause} is an instance of {@link ThrowableCause} it is returned as is. Otherwise, this method
     * constructs a new instance through {@link #ThrowableCause(Throwable)}.
     *
     * @param cause The {@link Throwable} to map to a {@link ThrowableCause}.
     * @return A {@link ThrowableCause} based on the given {@code cause}, or the {@code cause} as-is if it is an
     * instance of {@code ThrowableCause}.
     */
    public static ThrowableCause asCause(Throwable cause) {
        return cause instanceof ThrowableCause ? (ThrowableCause) cause : new ThrowableCause(cause);
    }

    /**
     * Construct a {@link ThrowableCause} based on the given {@code throwable}, truncating the message to a maximum size
     * of {@link #TRUNCATED_MESSAGE_SIZE}.
     * <p>
     * Should be used to ensure the {@link Cause} fits in the desired dead-letter storage solution.
     *
     * @param throwable The {@link Throwable} to adjust to a {@link ThrowableCause}.
     * @return A {@link ThrowableCause} based on the given {@code throwable} for which the message is truncated to
     * {@link #TRUNCATED_MESSAGE_SIZE}.
     */
    public static ThrowableCause truncated(Throwable throwable) {
        return truncated(throwable, TRUNCATED_MESSAGE_SIZE);
    }

    /**
     * Construct a {@link ThrowableCause} based on the given {@code throwable}, truncating the message to the given
     * {@code messageSize}.
     * <p>
     * Should be used to ensure the {@link Cause} fits in the desired dead-letter storage solution.
     *
     * @param throwable   The {@link Throwable} to adjust to a {@link ThrowableCause}.
     * @param messageSize The size to truncate the {@link Throwable#getMessage()} to, to be able to fit in databases.
     * @return A {@link ThrowableCause} based on the given {@code throwable} for which the message is truncated to given
     * {@code messageSize}.
     */
    public static ThrowableCause truncated(Throwable throwable, int messageSize) {
        return new ThrowableCause(
                throwable.getClass().getName(),
                throwable.getMessage() == null ? null :
                    throwable.getMessage().substring(0, Math.min(throwable.getMessage().length(), messageSize))
        );
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ThrowableCause that = (ThrowableCause) o;
        return Objects.equals(type, that.type) && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, message);
    }

    @Override
    public String toString() {
        return "Cause{type=[" + type + "]-message=[" + message + "]}";
    }
}
