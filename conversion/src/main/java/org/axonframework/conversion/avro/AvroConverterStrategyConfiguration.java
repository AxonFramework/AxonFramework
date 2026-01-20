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

package org.axonframework.conversion.avro;

/**
 * Configuration for the Avro converter strategy.
 * @param performAvroCompatibilityCheck Should schema compatibility check be performed prio conversion.
 * @param includeSchemasInStackTraces Should Avro schemas be included into stack traces on errors.
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public record AvroConverterStrategyConfiguration(
        boolean performAvroCompatibilityCheck,
        boolean includeSchemasInStackTraces
) {

    /**
     * Default configuration.
     */
    public static final AvroConverterStrategyConfiguration DEFAULT
            = new AvroConverterStrategyConfiguration(true, false);
}
