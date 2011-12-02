package org.axonframework.serializer.converters;

import org.apache.commons.io.IOUtils;
import org.axonframework.serializer.CannotConvertBetweenTypesException;
import org.axonframework.serializer.ContentTypeConverter;
import org.axonframework.serializer.IntermediateRepresentation;
import org.axonframework.serializer.SimpleIntermediateRepresentation;

import java.io.IOException;
import java.io.InputStream;

/**
 * Converter that converts an InputStream to a byte array. This converter simply reads all contents from the
 * input stream and returns that as an array.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class InputStreamToByteArrayConverter implements ContentTypeConverter<InputStream, byte[]> {

    @Override
    public Class<InputStream> expectedSourceType() {
        return InputStream.class;
    }

    @Override
    public Class<byte[]> targetType() {
        return byte[].class;
    }

    @Override
    public IntermediateRepresentation<byte[]> convert(final IntermediateRepresentation<InputStream> original) {
        try {
            return new SimpleIntermediateRepresentation<byte[]>(original.getType(), targetType(),
                                                                IOUtils.toByteArray(original.getData()));
        } catch (IOException e) {
            throw new CannotConvertBetweenTypesException("Unable to convert inputstream to byte[]. "
                                                                 + "Error while reading from Stream.", e);
        }
    }
}
