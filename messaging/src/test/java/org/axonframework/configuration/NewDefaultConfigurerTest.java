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

package org.axonframework.configuration;

import org.axonframework.commandhandling.SimpleCommandHandlingComponent;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.QualifiedName;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;

class NewDefaultConfigurerTest {

    @Test
    void commandBusConfiguring() {
        NewDefaultConfigurer.configurer()
                            // We have a base configuration
                            .registerComponent(AxonServerCommandBusConfiguration.class,
                                               c -> new AxonServerCommandBusConfiguration())
                            // Users can customize this configuration however many times they like

                            .registerDecorator(AxonServerCommandBusConfiguration.class,
                                               5,
                                               (config, properties) -> properties.withTransactionTimeout(1000))

                            // If the moon is aligned, we can also disable auto snapshots
                            .registerDecorator(AxonServerCommandBusConfiguration.class,
                                               500,
                                               (config, properties) -> {
                                                   if (Math.random() > 0.5) {
                                                       return properties.withoutAutoSnapshot();
                                                   }
                                                   return properties;
                                               })

                            // The construction of the component can now use this configuration as a parameter
                            .registerComponent(
                                    CommandBus.class,
                                    (c) -> new AxonServerCommandBus(
                                            c.getComponent(AxonServerConnectionManager.class),
                                            c.getComponent(AxonServerCommandBusConfiguration.class)
                                    )
                            )


                            // A decorator can do the same!
                            .registerComponent(TracingCommandBusProperties.class,
                                               c -> new TracingCommandBusProperties()
                            )
                            // And customize is at any point
                            .registerDecorator(TracingCommandBusProperties.class,
                                               Integer.MAX_VALUE,
                                               (config, properties) -> properties.withHandleTraceName(
                                                       "This trace is NOT awesome")
                            )

                            // We can register this decorator for the commabnd bus, with configuration
                            .registerDecorator(
                                    CommandBus.class,
                                    50,
                                    (c, delegate) ->
                                            new TracingCommandBus(delegate,
                                                                  c.getComponent(TracingCommandBusProperties.class)
                                            )
                            )

                            // Ah wait - we also want a locking command bus, no need for configuration
                            .registerDecorator(
                                    CommandBus.class,
                                    100,
                                    (config, delegate) -> new LockingCommandBus(delegate)
                            )


                            .registerCommandHandler(config -> new SimpleCommandHandlingComponent()
                                    .subscribe(
                                            new QualifiedName(TestCommand.class),
                                            (command, context) -> {
                                                System.out.println("Handle test command");
                                                return MessageStream.empty();
                                            }
                                    )
                            )
                            .registerCommandHandler(config -> (command, context) -> {
                                // TODO for this to work, a CommandHandler should be able to provide it's own QualifiedName some how.
                                System.out.println("Handle test command");
                                return MessageStream.empty();
                            });
    }

    // The cool thing is, this works AMAZING for bean definitions with properties:
    @Bean
    public ComponentDecorator<AxonServerCommandBusConfiguration> autoSnapshotAxonServerCommandBusConfiguration() {
        return (config, properties) -> properties.withoutAutoSnapshot();
    }

    // Or to decorate components:
    @Bean
    public ComponentDecorator<CommandBus> myTracingCommandBus() {
        return (config, delegate) -> new TracingCommandBus(delegate,
                                                           config.getComponent(TracingCommandBusProperties.class));
    }

    class TestCommand {

    }

    public interface CommandBus {
        // Empty for now
    }


    public class AxonServerCommandBus implements CommandBus {

        private final AxonServerConnectionManager connectionManager;
        private final AxonServerCommandBusConfiguration configuration;

        public AxonServerCommandBus(AxonServerConnectionManager connectionManager,
                                    AxonServerCommandBusConfiguration configuration) {
            this.connectionManager = connectionManager;
            this.configuration = configuration;
        }
    }

    class AxonServerCommandBusConfiguration {

        private int transactionTimeout = 30000;
        private boolean autoSnapshot = true;

        public AxonServerCommandBusConfiguration() {
            // Defaults
        }

        private AxonServerCommandBusConfiguration(int transactionTimeout, boolean autoSnapshot) {
            this.transactionTimeout = transactionTimeout;
            this.autoSnapshot = autoSnapshot;
        }

        public int getTransactionTimeout() {
            return transactionTimeout;
        }

        public AxonServerCommandBusConfiguration withoutAutoSnapshot() {
            return new AxonServerCommandBusConfiguration(transactionTimeout, false);
        }

        public AxonServerCommandBusConfiguration withTransactionTimeout(int transactionTimeout) {
            return new AxonServerCommandBusConfiguration(transactionTimeout, autoSnapshot);
        }
    }

    class AxonServerConnectionManager {

    }

    class TracingCommandBusProperties {

        private String handleTraceName = "This trace is awesome";

        public TracingCommandBusProperties() {
            // Defaults
        }

        private TracingCommandBusProperties(String handleTraceName) {
            this.handleTraceName = handleTraceName;
        }

        public String getHandleTraceName() {
            return handleTraceName;
        }

        public TracingCommandBusProperties withHandleTraceName(String handleTraceName) {
            return new TracingCommandBusProperties(handleTraceName);
        }
    }

    class TracingCommandBus implements CommandBus {

        private final CommandBus delegate;
        private final TracingCommandBusProperties properties;

        TracingCommandBus(CommandBus delegate, TracingCommandBusProperties properties) {
            this.delegate = delegate;
            this.properties = properties;
        }
    }


    class LockingCommandBus implements CommandBus {

        private final CommandBus delegate;

        LockingCommandBus(CommandBus delegate) {
            this.delegate = delegate;
        }
    }

}