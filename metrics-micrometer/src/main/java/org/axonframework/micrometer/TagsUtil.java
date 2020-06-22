package org.axonframework.micrometer;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.axonframework.messaging.Message;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Utility class for micrometer tag management.
 * <p>
 * Contains 'static final' fields which represent the micrometer tag KEY that should be consistent across different
 * metrics, and functions responsible for creating the Micrometer {@link Tag}s based on the message that is ingested.
 *
 * @author Ivan Dugalic
 * @since 4.4
 */
public class TagsUtil {

    private TagsUtil() {
    }

    /**
     * The micrometer {@link Tag} key that represents the Axon message payload type
     */
    public static final String PAYLOAD_TYPE_TAG = "payloadType";
    /**
     * The micrometer {@link Tag} key that represents the Axon event processor name
     */
    public static final String PROCESSOR_NAME_TAG = "processorName";
    /**
     * The function for creating the Micrometer {@link Tag}s based on the message payload type.
     */
    public static final Function<Message<?>, Iterable<Tag>> PAYLOAD_TYPE_TAGGER_FUNCTION = message -> Tags.of(
            PAYLOAD_TYPE_TAG,
            message.getPayloadType().getSimpleName());

    /**
     * The function for creating the Micrometer {@link Tag}s based on the message metadata.
     */
    public static final Function<Message<?>, Iterable<Tag>> META_DATA_TAGGER_FUNCTION = message -> message
            .getMetaData().entrySet().stream().map(it -> Tag.of(it.getKey(), it.getValue().toString()))
            .collect(Collectors.toList());
}
