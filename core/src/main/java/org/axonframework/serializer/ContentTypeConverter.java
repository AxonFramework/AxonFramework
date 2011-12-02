package org.axonframework.serializer;

/**
 * Interface describing a mechanism that converts the data type of IntermediateRepresentations of SerializedObjects for
 * Upcasters. Different upcasters may require different data type (e.g. <code>byte[]</code> or
 * <code>InputStream</code>), or may produce a different data type than they consume.
 *
 * @param <S> The expected source type
 * @param <T> The output type
 * @author Allard Buijze
 * @since 2.0
 */
public interface ContentTypeConverter<S, T> {

    /**
     * The expected type of input data.
     *
     * @return the expected data format in IntermediateRepresentation
     */
    Class<S> expectedSourceType();

    /**
     * The returned type of IntermediateRepresentation
     *
     * @return the output data format in IntermediateRepresentation
     */
    Class<T> targetType();

    /**
     * Converts the data format of the given <code>original</code> IntermediateRepresentation to the target data type.
     *
     * @param original The source to convert
     * @return the converted representation
     */
    IntermediateRepresentation<T> convert(IntermediateRepresentation<S> original);
}
