package org.axonframework.micrometer;

import io.micrometer.core.instrument.Tag;

/**
 * Utility class for micrometer tag management.
 * <p>
 * Contains 'static final' fields which represent the micrometer tag KEY that should be consistent across different
 * metrics.
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
}
