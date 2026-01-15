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

package org.axonframework.axonserver.connector;

import io.axoniq.axonserver.grpc.MetaDataValue;
import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to convert between Axon Server metadata values and a simple {@link Map} of String key-value pairs.
 * <p>
 * This class provides methods to convert a {@link Map} of String key-value pairs to a {@link Map} of Axon Server
 * {@link MetaDataValue} objects and vice versa.
 *
 * @author Steven van Beelen
 * @author Mitchell Herrijgers
 * @author Allard Buijze
 * @since 5.0.0
 */
@Internal
public final class MetadataConverter {

    private MetadataConverter() {
        // Utility class
    }

    /**
     * Converts a {@link Map} of String key-value pairs to a {@link Map} of Axon Server {@link MetaDataValue} objects.
     *
     * @param source The source map containing String key-value pairs.
     * @return A map where each value is converted to an Axon Server {@link MetaDataValue}.
     */
    @Nonnull
    public static Map<String, MetaDataValue> convertGrpcToMetadataValues(@Nonnull Map<String, String> source) {
        Map<String, MetaDataValue> result = new HashMap<>();
        source.forEach((k, v) -> {
            MetaDataValue convertedValue = convertToTextMetaDataValue(v);
            result.put(k, convertedValue);
        });
        return result;
    }

    private static MetaDataValue convertToTextMetaDataValue(String value) {
        return MetaDataValue.newBuilder().setTextValue(value).build();
    }

    /**
     * Converts a {@link Map} of Axon Server {@link MetaDataValue} objects to a {@link Map} of String key-value pairs.
     *
     * @param source The source map containing Axon Server {@link MetaDataValue} objects.
     * @return A map where each value is converted to a String representation.
     */
    @Nonnull
    public static Map<String, String> convertMetadataValuesToGrpc(@Nonnull Map<String, MetaDataValue> source) {
        Map<String, String> result = new HashMap<>();
        source.forEach((k, v) -> {
            String convertedValue = convertFromMetaDataValue(v);
            if (convertedValue != null) {
                result.put(k, convertedValue);
            }
        });
        return result;
    }

    private static String convertFromMetaDataValue(MetaDataValue value) {
        return switch (value.getDataCase()) {
            case TEXT_VALUE -> value.getTextValue();
            case DOUBLE_VALUE -> Double.toString(value.getDoubleValue());
            case NUMBER_VALUE -> Long.toString(value.getNumberValue());
            case BOOLEAN_VALUE -> Boolean.toString(value.getBooleanValue());
            default -> null;
        };
    }
}