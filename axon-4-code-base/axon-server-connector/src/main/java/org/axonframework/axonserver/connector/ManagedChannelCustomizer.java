/*
 * Copyright (c) 2010-2023. Axon Framework
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

import io.grpc.ManagedChannelBuilder;

import java.util.function.UnaryOperator;

/**
 * Customizer to add more customizations to a managed channel to Axon Server.
 *
 * @author Marc Gathier
 * @since 4.4.3
 */
@FunctionalInterface
public interface ManagedChannelCustomizer extends UnaryOperator<ManagedChannelBuilder<?>> {

    /**
     * Returns a no-op {@link ManagedChannelCustomizer}.
     *
     * @return a no-op {@link ManagedChannelCustomizer}
     */
    static ManagedChannelCustomizer identity() {
        return c -> c;
    }
}
