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

package org.axonframework.extension.metrics.micrometer;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.Nonnull;
import org.axonframework.messaging.core.Message;
import org.axonframework.messaging.core.MessageType;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for Micrometer tag management.
 * <p>
 * Contains {@code static final} fields which represent the micrometer tag KEY that should be consistent across
 * different metrics, and functions responsible for creating the Micrometer {@link Tag Tags} based on the
 * {@link Message} that is ingested by a {@link org.axonframework.messaging.monitoring.MessageMonitor}.
 *
 * @author Ivan Dugali
 * @author Steven van Beelen
 * @since 4.4.0
 */
public class TagsUtil {

    private TagsUtil() {
    }

    /**
     * The micrometer {@link Tag} key that represents Axon Framework's {@link Message#type() message type}
     * {@link MessageType#name() name}.
     */
    public static final String MESSAGE_TYPE_TAG = "messageType";

    /**
     * The micrometer {@link Tag} key that represents the Axon Framework's
     * {@link org.axonframework.messaging.eventhandling.processing.EventProcessor} name.
     */
    public static final String PROCESSOR_NAME_TAG = "processorName";

    /**
     * A function for creating the Micrometer {@link Tag} based on the {@link Message#type() message type}
     * {@link MessageType#name() name}.
     */
    public static final Function<Message, Iterable<Tag>> MESSAGE_TYPE_TAGGER =
            message -> Tags.of(MESSAGE_TYPE_TAG, message.type().name());

    /**
     * A function for creating the Micrometer {@link Tag Tags} based on the {@link Message message's}
     * {@link Message#metadata() metadata}.
     */
    public static final Function<Message, Iterable<Tag>> METADATA_TAGGER = message ->
            message.metadata().entrySet().stream()
                   .map(it -> Tag.of(it.getKey(), it.getValue()))
                   .collect(Collectors.toList());

    /**
     * A function for creating no Micrometer {@link Tag Tags} at all.
     */
    public static final Function<Message, Iterable<Tag>> NO_OP = m -> List.of();

    /**
     * A function for creating a Micrometer {@link Tag} based on the given {@code processorName} only, using the
     * {@link #PROCESSOR_NAME_TAG} key.
     *
     * @param processorName the name of the processor to add under the {@link #PROCESSOR_NAME_TAG} key as a tag
     * @return a function for creating a Micrometer {@link Tag} based on the given {@code processorName} only, using the
     * {@link #PROCESSOR_NAME_TAG} key
     */
    public static Function<Message, Iterable<Tag>> processorNameTag(@Nonnull String processorName) {
        return message -> Tags.of(PROCESSOR_NAME_TAG, processorName);
    }

    /**
     * A function for creating a Micrometer {@link Tag Tags} based on the given {@code processorName}, using the
     * {@link #PROCESSOR_NAME_TAG} key, and the {@link #MESSAGE_TYPE_TAGGER}.
     *
     * @param processorName the name of the processor to add under the {@link #PROCESSOR_NAME_TAG} key as a tag
     * @return a function for creating a Micrometer {@link Tag Tags} based on the given {@code processorName}, using the
     * {@link #PROCESSOR_NAME_TAG} key, and the {@link #MESSAGE_TYPE_TAGGER}
     */
    public static Function<Message, Iterable<Tag>> processorTags(@Nonnull String processorName) {
        return message -> Tags.of(PROCESSOR_NAME_TAG, processorName)
                              .and(MESSAGE_TYPE_TAGGER.apply(message));
    }
}
