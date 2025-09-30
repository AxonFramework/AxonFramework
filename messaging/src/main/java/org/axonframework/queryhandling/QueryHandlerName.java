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

package org.axonframework.queryhandling;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotations.Internal;
import org.axonframework.messaging.QualifiedName;

import static java.util.Objects.requireNonNull;

/**
 * A combination of two {@link QualifiedName QualifiedNames}, one for the query handled by a {@link QueryHandler} and
 * another one specifying the name of the response returned by a {@code QueryHandler}.
 *
 * @param queryName    The name of the query a {@link QueryHandler} handlers.
 * @param responseName The name of the response a {@link QueryHandler} returns.
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public record QueryHandlerName(
        @Nonnull QualifiedName queryName,
        @Nonnull QualifiedName responseName
) {

    /**
     * Compact constructor asserting whether the {@code queryName} and {@code responseName} are non-null.
     *
     * @param queryName    The name of the query a {@link QueryHandler} handlers.
     * @param responseName The name of the response a {@link QueryHandler} returns.
     */
    public QueryHandlerName {
        requireNonNull(queryName, "The qualifiedName cannot be null.");
        requireNonNull(responseName, "The qualifiedName cannot be null.");
    }
}
