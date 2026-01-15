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

import jakarta.annotation.Nonnull;
import org.apache.avro.message.SchemaStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for the AvroConverter.
 *
 * @param strategies                                converter strategies to support different representation of objects
 *                                                  (SpecificRecordBase, Avro4K).
 * @param includeDefaultAvroConverterStrategies     flag to include the default strategies.
 * @param schemaStore                               schema store to resolve Avro schemas.
 * @param schemaIncompatibilityChecker              schema incompatibility checker.
 * @param avroConverterStrategyConfiguration         configuration for Avro converter strategy.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since 5.0.0
 */
public record AvroConverterConfiguration(
        @Nonnull List<AvroConverterStrategy> strategies,
        boolean includeDefaultAvroConverterStrategies,
        @Nonnull SchemaStore schemaStore,
        @Nonnull SchemaIncompatibilityChecker schemaIncompatibilityChecker,
        @Nonnull AvroConverterStrategyConfiguration avroConverterStrategyConfiguration
) {

    /**
     * Constructor validating that required values are provided.
     *
     * @param strategies                                list of avro converter strategies, must not be null.
     * @param includeDefaultAvroConverterStrategies flag to include the default strategies.
     * @param schemaStore                               schema store to resolve Avro schemas.
     * @param schemaIncompatibilityChecker              schema incompatibility checker.
     * @param avroConverterStrategyConfiguration        configuration for Avro converter strategy.
     */
    public AvroConverterConfiguration {
        Objects.requireNonNull(strategies, "Avro converter strategies cannot be null");
        Objects.requireNonNull(schemaStore, "Schema store cannot be null");
        Objects.requireNonNull(schemaIncompatibilityChecker,
                               "Schema incompatibility checker cannot be null");
        Objects.requireNonNull(avroConverterStrategyConfiguration,
                               "Avro converter strategy configuration cannot be null");
        if (!includeDefaultAvroConverterStrategies && strategies.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one Avro converter strategy is required and no default strategies will be used");
        }
    }

    /**
     * Compact constructor, using provided schema store, default strategies, performing compatibility checks but not
     * reporting schemas in stack traces on any conversion errors.
     *
     * @param schemaStore schema store to use, must not be null.
     */
    public AvroConverterConfiguration(@Nonnull SchemaStore schemaStore) {
        this(
                new ArrayList<>(),
                true,
                schemaStore,
                new DefaultSchemaIncompatibilityChecker(),
                AvroConverterStrategyConfiguration.DEFAULT
        );
    }

    /**
     * Returns a new configuration with an additional Avro converter strategy.
     *
     * @param strategy provided strategy to use, for example the one compatible with Avro4K data classes.
     * @return configuration instance.
     */
    public AvroConverterConfiguration addConverterStrategy(@Nonnull AvroConverterStrategy strategy) {
        List<AvroConverterStrategy> strategies = new ArrayList<>(this.strategies);
        strategies.add(Objects.requireNonNull(strategy, "Avro converter strategy cannot be null"));
        return new AvroConverterConfiguration(strategies,
                                              this.includeDefaultAvroConverterStrategies,
                                              this.schemaStore,
                                              this.schemaIncompatibilityChecker,
                                              this.avroConverterStrategyConfiguration
        );
    }

    /**
     * Returns new configuration using (or not) default Avro converter strategies, based on the provided flag.
     * <p>
     * Please note, that at least one Avro Strategy is required for the converter. If you intend not to use default
     * strategies, add your customer strategy using
     * {@link AvroConverterConfiguration#addConverterStrategy(AvroConverterStrategy)} first.
     * </p>
     *
     * @param includeDefaultAvroConverterStrategies flag to use default strategy (`true`) or not to use it.
     * @return new configuration.
     */
    public AvroConverterConfiguration includeDefaultAvroConverterStrategies(
            boolean includeDefaultAvroConverterStrategies) {
        return new AvroConverterConfiguration(this.strategies,
                                              includeDefaultAvroConverterStrategies,
                                              this.schemaStore,
                                              this.schemaIncompatibilityChecker,
                                              this.avroConverterStrategyConfiguration
        );
    }

    /**
     * Creates a new configuration using provided schema incompatibility checker.
     *
     * @param schemaIncompatibilityChecker instance responsible for schema compatibility checks.
     * @return new configuration.
     */
    public AvroConverterConfiguration schemaIncompatibilityChecker(
            @Nonnull SchemaIncompatibilityChecker schemaIncompatibilityChecker) {
        return new AvroConverterConfiguration(this.strategies,
                                              this.includeDefaultAvroConverterStrategies,
                                              this.schemaStore,
                                              Objects.requireNonNull(schemaIncompatibilityChecker,
                                                                     "Schema incompatibility checker cannot be null"),
                                              this.avroConverterStrategyConfiguration
        );
    }

    /**
     * Creates a new configuration reporting (or not) reader and writer schema in stack traces on errors, based on the
     * provided flag.
     *
     * @param includeSchemasInStackTraces flag controlling if the schema reporting is included in stack traces.
     * @return new configuration.
     */
    public AvroConverterConfiguration includeSchemasInStackTraces(boolean includeSchemasInStackTraces) {
        return new AvroConverterConfiguration(this.strategies,
                                              this.includeDefaultAvroConverterStrategies,
                                              this.schemaStore,
                                              this.schemaIncompatibilityChecker,
                                              new AvroConverterStrategyConfiguration(
                                                      this.avroConverterStrategyConfiguration
                                                              .performAvroCompatibilityCheck(),
                                                      includeSchemasInStackTraces
                                              )
        );
    }

    /**
     * Creates a new configuration performing (or not) compatibility checks, based on the provided flag.
     *
     * @param performAvroCompatibilityCheck flag controlling if the check should be performed prio conversion.
     * @return new configuration.
     */
    public AvroConverterConfiguration performAvroCompatibilityCheck(boolean performAvroCompatibilityCheck) {
        return new AvroConverterConfiguration(this.strategies,
                                              this.includeDefaultAvroConverterStrategies,
                                              this.schemaStore,
                                              this.schemaIncompatibilityChecker,
                                              new AvroConverterStrategyConfiguration(
                                                      performAvroCompatibilityCheck,
                                                      this.avroConverterStrategyConfiguration.includeSchemasInStackTraces()
                                              )
        );
    }
}
