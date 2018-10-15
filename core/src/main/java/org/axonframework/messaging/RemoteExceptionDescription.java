/*
 * Copyright (c) 2010-2018. Axon Framework
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Description of an Exception received from a remote source. Allows the correct de-/serialization of Exceptions to be
 * send over the wire without failing on a potential ClassCastException.
 *
 * @author Steven van Beelen
 * @since 4.0
 */
public class RemoteExceptionDescription implements Serializable {

    private static final String DELIMITER = ": ";

    private final List<String> descriptions;

    @JsonCreator
    private RemoteExceptionDescription(@JsonProperty("descriptions") List<String> descriptions) {
        this.descriptions = new ArrayList<>(descriptions);
    }

    /**
     * Provide a description as a {@link List} of {@link String}s of all the causes in the given {@code exception}.
     *
     * @param exception a {@link Throwable} to create a description of
     * @return a {@link List} of {@link String} describing the given {@link Exception}
     */
    public static RemoteExceptionDescription describing(Throwable exception) {
        return new RemoteExceptionDescription(createDescription(exception, new ArrayList<>()));
    }

    private static List<String> createDescription(Throwable exception, List<String> descriptions) {
        descriptions.add(exception.getClass().getName() + DELIMITER + exception.getMessage());
        Throwable cause = exception.getCause();
        return cause != null ? createDescription(cause, descriptions) : descriptions;
    }

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
                sb.append("\nCaused by ");
            }
            sb.append(descriptions.get(i));
        }
        return sb.toString();
    }
}
