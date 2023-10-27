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

package org.axonframework.axonserver.connector.util;

import io.axoniq.axonserver.grpc.ErrorMessage;

import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * Utility class used to serializer {@link Throwable}s into {@link ErrorMessage}s.
 *
 * @author Marc Gathier
 * @since 4.0
 */
public abstract class ExceptionSerializer {

    private ExceptionSerializer() {
        // Utility class
    }

    /**
     * Serializes a given {@link Throwable} into an {@link ErrorMessage}.
     *
     * @param clientLocation the name of the client were the {@link ErrorMessage} originates from
     * @param t              the {@link Throwable} to base this {@link ErrorMessage} on
     * @return the {@link ErrorMessage} originating from the given {@code clientLocation} and based on the
     * {@link Throwable}
     */
    public static ErrorMessage serialize(String clientLocation, Throwable t) {
        ErrorMessage.Builder builder =
                ErrorMessage.newBuilder()
                            .setLocation(getOrDefault(clientLocation, ""))
                            .setMessage(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        builder.addDetails(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        while (t.getCause() != null) {
            t = t.getCause();
            builder.addDetails(t.getMessage() == null ? t.getClass().getName() : t.getMessage());
        }
        return builder.build();
    }

}
