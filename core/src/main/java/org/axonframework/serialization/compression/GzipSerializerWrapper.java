package org.axonframework.serialization.compression;

import org.axonframework.serialization.ChainingConverter;
import org.axonframework.serialization.Converter;
import org.axonframework.serialization.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * AbstractSerializerCompressionWrapper implementation that used gzip compression
 *
 * @author Michael Willemse
 */
public class GzipSerializerWrapper extends AbstractSerializerCompressionWrapper {

    /**
     * Initializes GzipSerializerWrapper with the embedded serializer as parameter
     * @param embeddedSerializer embedded serializer
     */
    public GzipSerializerWrapper(Serializer embeddedSerializer) {
        this(embeddedSerializer, new ChainingConverter());
    }

    /**
     * Initializes GzipSerializerWrapper with the embedded serializer as parameter
     * Invokes the constructor of the {@link AbstractSerializerCompressionWrapper}
     * @param embeddedSerializer serializer that must be able to serialize to a byte array otherwise the constructor
     *                           throws an IllegalArgumentException.
     * @param converter          converter able to convert to and from byte[], the working format in this serialization
     *                           wrapper.
     */
    public GzipSerializerWrapper(Serializer embeddedSerializer, Converter converter) {
        super(embeddedSerializer, converter);
    }

    @Override
    protected byte[] doCompress(byte[] uncompressedData) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(uncompressedData.length);
             GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
            gzipOS.write(uncompressedData);
            gzipOS.close();
            return bos.toByteArray();
        }
    }

    @Override
    protected byte[] doDecompress(byte[] compressedData) throws IOException {
        if(compressedData != null && compressedData.length > 2 &&
                compressedData[0] == (byte) 0x1f && compressedData[1] == (byte) 0x8b) {

            try (ByteArrayInputStream bis = new ByteArrayInputStream(compressedData);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 GZIPInputStream gzipIS = new GZIPInputStream(bis)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gzipIS.read(buffer)) != -1) {
                    bos.write(buffer, 0, len);
                }
                return bos.toByteArray();
            }
        } else {
            return compressedData;
        }
    }
}
