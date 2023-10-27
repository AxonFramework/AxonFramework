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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.common.ExceptionUtils;

import java.beans.ConstructorProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Description of an Exception received from a remote source. Allows for correct de-/serialization of the cause of
 * Exceptions without requiring any knowledge on the Exception classes.
 *
 * @author Steven van Beelen
 * @since 4.0
 */
public class RemoteExceptionDescription implements Serializable {

    private static final String DELIMITER = ": ";
    private static final String CAUSED_BY = "\nCaused by ";

    private final List<String> descriptions;
    private final boolean persistent;

    /**
     * Provide a description as a {@link List} of {@link String}s of all the causes in the given {@code exception}.
     *
     * @param exception a {@link Throwable} to create a description of
     * @return a {@link List} of {@link String} describing the given {@link Exception}
     */
    public static RemoteExceptionDescription describing(Throwable exception) {
        final boolean isPersistent = ExceptionUtils.isExplicitlyNonTransient(exception);
        return new RemoteExceptionDescription(createDescription(exception, new ArrayList<>()), isPersistent);
    }

    private static List<String> createDescription(Throwable exception, List<String> descriptions) {
        descriptions.add(exception.getClass().getName() + DELIMITER + exception.getMessage());
        Throwable cause = exception.getCause();
        return cause != null ? createDescription(cause, descriptions) : descriptions;
    }

    /**
     * Initialize a RemoteExceptionDescription with given {@code descriptions} describing the exception chain on the
     * remote end of communication
     *
     * @param descriptions a {@link List} of {@link String}s, each describing a single "cause" on the remote end
     */
    public RemoteExceptionDescription(@JsonProperty("descriptions") List<String> descriptions) {
        this(descriptions, false);
    }

    /**
     * Initialize a RemoteExceptionDescription with given {@code descriptions} describing the exception
     * chain on the remote end of communication. The {@code persistent} indicator defines whether the exception is
     * considered persistent or transient.
     *
     * @param descriptions a {@link List} of {@link String}s, each describing a single "cause" on the remote end
     * @param persistent an indicator whether the exception is considered persistent or transient
     */
    @JsonCreator
    @ConstructorProperties({"descriptions, persistent"})
    public RemoteExceptionDescription(@JsonProperty("descriptions") List<String> descriptions,
                                      @JsonProperty("persistent") boolean persistent) {
        this.persistent = persistent;
        this.descriptions = new ArrayList<>(descriptions);
    }

    /**
     * Returns whether the exception is considered persistent or not.
     *
     * @return the persistency indicator
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Returns the {@link List} of {@link String}s describing the causes of the exception on the remote end.
     *
     * @return the descriptions of the causes as a {@link List} of {@link String}s
     */
    public List<String> getDescriptions() {
        return descriptions;
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptions);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final RemoteExceptionDescription other = (RemoteExceptionDescription) obj;
        return Objects.equals(this.descriptions, other.descriptions);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < descriptions.size(); i++) {
            if (i != 0) {
                sb.append(CAUSED_BY);
            }
            sb.append(descriptions.get(i));
        }
        return sb.toString();
    }
}
