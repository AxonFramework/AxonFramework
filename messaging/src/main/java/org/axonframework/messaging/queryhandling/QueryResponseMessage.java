/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.axonframework.messaging.queryhandling;

import org.axonframework.common.TypeReference;
import org.axonframework.messaging.core.ResultMessage;
import org.axonframework.conversion.Converter;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * A {@link ResultMessage} implementation that contains the result of query handling.
 *
 * @author Allard Buijze
 * @since 3.2.0
 */
public interface QueryResponseMessage extends ResultMessage {

    @Override
    QueryResponseMessage withMetadata(Map<String, String> metadata);

    @Override
    QueryResponseMessage andMetadata(Map<String, String> additionalMetadata);

    @Override
    default QueryResponseMessage withConvertedPayload(Class<?> type, Converter converter) {
        return withConvertedPayload((Type) type, converter);
    }

    @Override
    default QueryResponseMessage withConvertedPayload(TypeReference<?> type,
                                                      Converter converter) {
        return withConvertedPayload(type.getType(), converter);
    }

    @Override
    QueryResponseMessage withConvertedPayload(Type type, Converter converter);
}
