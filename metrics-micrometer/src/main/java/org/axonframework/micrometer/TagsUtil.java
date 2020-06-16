package org.axonframework.micrometer;

/**
 * Utility class for micrometer tag management.
 * <p>
 * Contains 'static final' fields which represent the micrometer tag KEY that should be consistent across different
 * metrics.
 */
public class TagsUtil {

    public static final String PAYLOAD_TYPE_TAG = "payloadType";
    public static final String PROCESSOR_NAME_TAG = "processorName";
}
