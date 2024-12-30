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

package org.axonframework.axonserver.connector.event;

import org.axonframework.common.Context;

/**
 * Utility class to obtain resources used in previous versions of Axon Framework.
 *
 * @author Allard Buijze
 * @since 5.0.0
 */
public abstract class LegacyResources {

    /**
     * The ResourceKey to obtain the Aggregate Identifier from an Event's context.
     */
    public static final Context.ResourceKey<String> AGGREGATE_IDENTIFIER_KEY = Context.ResourceKey.create("aggregateIdentifier");
    /**
     * The ResourceKey to obtain the Aggregate Type from an Event's context.
     */
    public static final Context.ResourceKey<String> AGGREGATE_TYPE_KEY = Context.ResourceKey.create("aggregateType");
    /**
     * The ResourceKey to obtain the Aggregate Sequence Number from an Event's context.
     */
    public static final Context.ResourceKey<Long> AGGREGATE_SEQUENCE_NUMBER_KEY = Context.ResourceKey.create("aggregateSequenceNumber");
}
