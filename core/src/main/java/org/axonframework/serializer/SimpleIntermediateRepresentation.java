package org.axonframework.serializer;

/**
 * Implementation of the IntermediateRepresentation that takes all properties as constructor parameters.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class SimpleIntermediateRepresentation<T> implements IntermediateRepresentation<T> {
    private final SerializedType type;
    private final Class<T> contentType;
    private final T contents;

    /**
     * Initializes a SimpleIntermediateRepresentation with given <code>type</code>, <code>contents</code> and
     * <code>contents</code>.
     *
     * @param type        The serialized type for this representation
     * @param contentType The data type of the contents
     * @param contents    The actual contents
     */
    public SimpleIntermediateRepresentation(SerializedType type, Class<T> contentType, T contents) {
        this.type = type;
        this.contentType = contentType;
        this.contents = contents;
    }

    @Override
    public Class<T> getContentType() {
        return contentType;
    }

    @Override
    public SerializedType getType() {
        return type;
    }

    @Override
    public T getData() {
        return contents;
    }
}
