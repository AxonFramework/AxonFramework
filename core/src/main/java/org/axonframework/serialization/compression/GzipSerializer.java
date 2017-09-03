package org.axonframework.serialization.compression;

import org.axonframework.serialization.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

/**
 * @author Michael Willemse
 */
public class GzipSerializer extends ByteArrayCompressionSerializer {

    public GzipSerializer(Serializer embeddedSerializer) {
        super(embeddedSerializer);
    }

    protected byte[] doCompress(byte[] uncompressedData) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(uncompressedData.length);
             GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
            gzipOS.write(uncompressedData);
            gzipOS.close();
            return bos.toByteArray();
        }
    }

    protected byte[] doDecompress(byte[] compressedData) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(compressedData);
             ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPInputStream gzipIS = new GZIPInputStream(bis)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIS.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        } catch (ZipException e) {
            throw new NotCompressedException("Content format not GZIP or compression method not supported.", e);
        }
    }
}
