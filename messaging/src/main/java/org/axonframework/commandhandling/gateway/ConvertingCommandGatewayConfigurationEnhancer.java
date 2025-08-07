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

package org.axonframework.commandhandling.gateway;

import jakarta.annotation.Nonnull;
import org.axonframework.common.annotation.Internal;
import org.axonframework.configuration.ComponentRegistry;
import org.axonframework.configuration.ConfigurationEnhancer;
import org.axonframework.serialization.Converter;

/**
 * Configuration enhancer for the {@link CommandGateway}, which decorates the regular {@link CommandGateway} with a
 * {@link ConvertingCommandGateway} using the {@link Converter} present in the {@link ComponentRegistry}.
 * <p>
 * The {@link ConvertingCommandGateway} wraps the {@link CommandResult} with said {@code Converter}, allowing for
 * deserialization of the result of command handling to the type the user expects.
 *
 * @author Mitchell Herrijgers
 * @author Allard Buijze
 * @since 5.0.0
 */
@Internal
public class ConvertingCommandGatewayConfigurationEnhancer implements ConfigurationEnhancer {

    /**
     * The order in which the {@link ConvertingCommandGateway} is applied to the {@link CommandGateway} in the
     * {@link ComponentRegistry}. As such, any decorator with a lower value will be applied to the delegate, and any
     * higher value will be applied to the {@link ConvertingCommandGateway} itself. Using the same value can either lead
     * to application of the decorator to the delegate or the converting command gateway, depending on the order of
     * registration.
     * <p>
     * The order of the {@link ConvertingCommandGateway} is set to {@code Integer.MIN_VALUE + 100} to ensure it is
     * applied very early in the configuration process, but not the earliest to allow for other decorators to be
     * applied.
     */
    public static final int CONVERTING_COMMAND_GATEWAY_ORDER = Integer.MIN_VALUE + 100;

    @Override
    public void enhance(@Nonnull ComponentRegistry registry) {
        registry.registerDecorator(CommandGateway.class, CONVERTING_COMMAND_GATEWAY_ORDER, (config, name, delegate) ->
                new ConvertingCommandGateway(delegate, config.getComponent(Converter.class)));
    }
}