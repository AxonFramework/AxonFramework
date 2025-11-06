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

package org.axonframework.messaging.core.correlation;

import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.Metadata;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@code CorrelationDataProvider} implementation that defines correlation data by the {@link Message#metaData()} key
 * names.
 * <p>
 * The metadata entries from {@link Message messages} matching these keys are returned as correlation data.
 *
 * @author Allard Buijze
 * @since 2.3.0
 */
public class SimpleCorrelationDataProvider implements CorrelationDataProvider {

    private final String[] headerNames;

    /**
     * Initializes a {@code SimpleCorrelationDataProvider} that returns the {@link Message#metadata()} entries of given
     * {@link Message messages} that match the given {@code metadataKeys} as correlation data.
     *
     * @param metadataKeys The keys of the {@link Message#metadata()} entries from {@link Message messages} to return as
     *                     correlation data.
     */
    public SimpleCorrelationDataProvider(String... metadataKeys) {
        this.headerNames = Arrays.copyOf(metadataKeys, metadataKeys.length);
    }

    @Nonnull
    @Override
    public Map<String, String> correlationDataFor(@Nonnull Message message) {
        if (headerNames.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, String> data = new HashMap<>();
        final Metadata metadata = message.metadata();
        for (String headerName : headerNames) {
            if (metadata.containsKey(headerName)) {
                data.put(headerName, metadata.get(headerName));
            }
        }
        return data;
    }
}
