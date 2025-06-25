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

package org.axonframework.usage;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.lifecycle.Phase;
import org.axonframework.usage.configuration.UsagePropertyProvider;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.axonframework.configuration.ComponentDefinition.ofType;

/**
 * A {@link ConfigurationEnhancer} that registers the {@link UpdateChecker} component. This component is responsible for
 * reporting anonymous usage data to the AxonIQ servers. It is registered during the external connections phase of the
 * lifecycle.
 *
 * @author Mitchell Herrijgers
 * @since 5.0.0
 */
@Internal
public class UpdateCheckerConfigurationEnhancer implements ConfigurationEnhancer {

    private final static AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public void enhance(@Nonnull ComponentRegistry componentRegistry) {
        if (initialized.compareAndSet(false, true)) {
            componentRegistry
                    .registerIfNotPresent(
                            ofType(UsagePropertyProvider.class)
                                    .withBuilder(c -> UsagePropertyProvider.create())
                    )
                    .registerIfNotPresent(
                            ofType(UpdateCheckerHttpClient.class)
                                    .withBuilder(c -> {
                                        UsagePropertyProvider propertyProvider = c.getComponent(UsagePropertyProvider.class);
                                        return new UpdateCheckerHttpClient(propertyProvider);
                                    }))
                    .registerIfNotPresent(
                            ofType(UpdateCheckerReporter.class)
                                    .withBuilder(c -> new LoggingUpdateCheckerReporter())
                    )
                    .registerIfNotPresent(
                            ofType(UpdateChecker.class)
                                    .withBuilder(c -> new UpdateChecker(
                                            c.getComponent(UpdateCheckerHttpClient.class),
                                            c.getComponent(UpdateCheckerReporter.class)
                                    ))
                                    .onStart(Phase.EXTERNAL_CONNECTIONS, UpdateChecker::start)
                                    .onShutdown(Phase.EXTERNAL_CONNECTIONS, UpdateChecker::stop)
                    );
        }
    }
}
