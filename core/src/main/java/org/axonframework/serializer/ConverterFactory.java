package org.axonframework.serializer;

/**
 * Interface describing a mechanism that provides instances of ContentTypeConverter for a given source and target data
 * type.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public interface ConverterFactory {

    /**
     * Returns a converter that is capable of converting IntermediateRepresentation object containing the given
     * <code>sourceContentType</code> to the given <code>targetContentType</code>.
     *
     * @param sourceContentType The type of data the converter accepts as input
     * @param targetContentType The type of data the converter produces
     * @param <S>               The source content type
     * @param <T>               The target content type
     * @return a converter capable of converting from the given <code>sourceContentType</code> to
     *         <code>targetContentType</code>
     *
     * @throws CannotConvertBetweenTypesException
     *          when no suitable converter can be found
     */
    <S, T> ContentTypeConverter getConverter(Class<S> sourceContentType, Class<T> targetContentType);
}
