/*
 * Copyright (c) 2010-2024. Axon Framework
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
package org.axonframework.serialization.avro;

/**
 * Configuration class used by the strategy to receive configuration from the {@link AvroSerializer.Builder} during
 * instantiation of {@link AvroSerializer}.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 4.11.0
 */
public class AvroSerializerStrategyConfig {

    private final boolean performAvroCompatibilityCheck;
    private final boolean includeSchemasInStackTraces;

    /**
     * Retrieves a builder for configuration.
     *
     * @return builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Configuration with defaults.
     */
    AvroSerializerStrategyConfig(Builder builder) {
        this.performAvroCompatibilityCheck = builder.performAvroCompatibilityCheck;
        this.includeSchemasInStackTraces = builder.includeSchemasInStackTraces;
    }

    /**
     * Returns the flag to perform compatibility checks.
     *
     * @return flag value.
     */
    public boolean performAvroCompatibilityCheck() {
        return performAvroCompatibilityCheck;
    }

    /**
     * Returns the flag to include schemas in stack traces.
     *
     * @return flag value.
     */
    public boolean includeSchemasInStackTraces() {
        return includeSchemasInStackTraces;
    }

    /**
     * Builder for the configuration.
     */
    public static class Builder {

        private boolean performAvroCompatibilityCheck = true;
        private boolean includeSchemasInStackTraces = false;

        /**
         * Sets the flag to perform compatibility check during deserialization.*
         *
         * @param performAvroCompatibilityCheck flag being set by the builder.
         * @return builder instance.
         */
        public Builder performAvroCompatibilityCheck(boolean performAvroCompatibilityCheck) {
            this.performAvroCompatibilityCheck = performAvroCompatibilityCheck;
            return this;
        }

        /**
         * Sets the flag to include schemas in stack traces.
         *
         * @param includeSchemasInStackTraces flag being set by the builder.
         * @return builder instance.
         */
        public Builder includeSchemasInStackTraces(boolean includeSchemasInStackTraces) {
            this.includeSchemasInStackTraces = includeSchemasInStackTraces;
            return this;
        }

        /**
         * Constructs configuration.
         *
         * @return configuration instance.
         */
        public AvroSerializerStrategyConfig build() {
            return new AvroSerializerStrategyConfig(this);
        }
    }
}
