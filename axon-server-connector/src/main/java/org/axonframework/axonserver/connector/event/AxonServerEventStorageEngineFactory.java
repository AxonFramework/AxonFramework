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

package org.axonframework.axonserver.connector.event;

import io.axoniq.axonserver.connector.AxonServerConnection;
import jakarta.annotation.Nonnull;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.configuration.Component;
import org.axonframework.configuration.ComponentFactory;
import org.axonframework.configuration.Configuration;
import org.axonframework.configuration.InstantiatedComponentDefinition;
import org.axonframework.configuration.LifecycleRegistry;
import org.axonframework.serialization.Converter;

import java.util.Optional;

import static org.axonframework.configuration.MessagingConfigurationDefaults.EVENT_CONVERTER_NAME;

/**
 * A {@link ComponentFactory} implementation that generates {@link AxonServerEventStorageEngine} instances.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
public class AxonServerEventStorageEngineFactory implements ComponentFactory<AxonServerEventStorageEngine> {

    /**
     * The expected prefix for <b>any</b> {@code name} given when {@link #construct(String, Configuration) constructing}
     * an instance. An {@link Optional#empty() empty Optional} will be returned if the {@code name} does not start with
     * {@code "storageEngine"}.
     */
    public static final String ENGINE_PREFIX = "storageEngine";

    /**
     * The {@code name} delimiter used when deriving the context name for the {@link AxonServerEventStorageEngine} under
     * construction. If the {@code name} when {@link #construct(String, Configuration) constructing} an instance does
     * not contain this delimiter, an {@link Optional#empty() empty Optional} will be returned.
     */
    public static final String CONTEXT_DELIMITER = "@";

    /**
     * Constructs an {@link AxonServerEventStorageEngine} for the given {@code context}, retrieving a
     * {@link AxonServerConnectionManager} and {@link Converter} from the given {@code config}.
     * <p>
     * The {@code context} is used to request an {@link AxonServerConnection} from the
     * {@code AxonServerConnectionManager}.
     *
     * @param context The name of the context for which to open an {@link AxonServerConnection} for the
     *                {@link AxonServerEventStorageEngine} under construction.
     * @param config  The configuration from which to retrieve an {@link AxonServerConnectionManager} and
     *                {@link Converter} for the {@link AxonServerEventStorageEngine} under construction.
     * @return An {@link AxonServerEventStorageEngine}, connecting to the given {@code context}.
     */
    @Nonnull
    public static AxonServerEventStorageEngine constructForContext(@Nonnull String context,
                                                                   @Nonnull Configuration config) {
        AxonServerConnection connection = config.getComponent(AxonServerConnectionManager.class)
                                                .getConnection(context);
        Converter eventConverter = config.getComponent(Converter.class, EVENT_CONVERTER_NAME);
        return new AxonServerEventStorageEngine(connection, eventConverter);
    }

    @Override
    @Nonnull
    public Class<AxonServerEventStorageEngine> forType() {
        return AxonServerEventStorageEngine.class;
    }

    @Override
    @Nonnull
    public Optional<Component<AxonServerEventStorageEngine>> construct(@Nonnull String name,
                                                                       @Nonnull Configuration config) {
        return contextNameFrom(name).map(context -> constructForContext(context, config))
                                    .map(engine -> new InstantiatedComponentDefinition<>(
                                            new Component.Identifier<>(forType(), name),
                                            engine
                                    ));
    }

    @Override
    public void registerShutdownHandlers(@Nonnull LifecycleRegistry registry) {
        // Nothing to do here
    }

    private static Optional<String> contextNameFrom(String name) {
        return name.startsWith(ENGINE_PREFIX + CONTEXT_DELIMITER)
                ? Optional.of(name.substring(name.indexOf(CONTEXT_DELIMITER)))
                : Optional.empty();
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("type", forType());
        descriptor.describeProperty("nameFormat", ENGINE_PREFIX + CONTEXT_DELIMITER + "{context-name}");
    }
}
