/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.io;

import org.axonframework.serializer.Serializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * @author Allard Buijze
 */
public class StreamingSegmenter extends OutputStream implements WritableByteChannel {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final Serializer serializer;
    private final Queue<ByteArrayInputStream[]> readyToRead;

    private ByteBuffer prefixBuffer = ByteBuffer.allocate(0);
    private int bytesRemainingInSection = 1;
    private Iterator<? extends MessageSegment> segments;
    private ByteArrayInputStream[] currentBatch;

    public StreamingSegmenter(Serializer serializer) {
        this.serializer = serializer;
        this.readyToRead = new ArrayBlockingQueue<ByteArrayInputStream[]>(2);
    }

    @Override
    public void write(int b) throws IOException {
        bytes.write(b);
        if (prefixBuffer.remaining() > 0) {
            prefixBuffer.put((byte) (b & 0xFF));
            calculateSectionSize();
        } else {
            bytesRemainingInSection--;
            if (bytesRemainingInSection == 0) {
                prepareNextSection(b);
            }
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        int remaining = len;
        while (remaining > 0) {
            final int offset = off + len - remaining;
            if (prefixBuffer.remaining() > 0) {
                final int length = Math.min(remaining, prefixBuffer.remaining());
                prefixBuffer.put(b, offset, length);
                bytes.write(b, offset, length);
                remaining -= length;
                calculateSectionSize();
            } else {
                final int bytesToWrite = Math.min(remaining, bytesRemainingInSection);
                bytes.write(b, offset, bytesToWrite);
                bytesRemainingInSection -= bytesToWrite;
                if (bytesRemainingInSection == 0) {
                    final int lastByteWrittenIndex = offset + bytesToWrite - 1;
                    prepareNextSection(b[lastByteWrittenIndex]);
                }
                remaining -= bytesToWrite;
            }
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        final int remaining = src.remaining();
        write(src.array(), src.arrayOffset(), remaining);
        return remaining;
    }

    private void calculateSectionSize() {
        if (prefixBuffer.remaining() == 0) {
            prefixBuffer.flip();
            if (prefixBuffer.capacity() == 2) {
                bytesRemainingInSection = prefixBuffer.getShort();
            } else if (prefixBuffer.capacity() == 4) {
                bytesRemainingInSection = prefixBuffer.getInt();
            }
        }
    }

    public int getReadyMessageCount() {
        return readyToRead.size() == 0 ? 0 : readyToRead.peek().length;
    }

    private void prepareNextSection(int lastByteRead) throws IOException {
        if (segments == null) {
            final MessageDefinition messageType = findMessageDefinitionOf(lastByteRead);
            segments = Arrays.asList(messageType.getSegments()).iterator();
        }
        if (segments != null && segments.hasNext()) {
            MessageSegment nextSegment = segments.next();
            if (nextSegment.isFixedLength()) {
                prefixBuffer = ByteBuffer.allocate(0);
                bytesRemainingInSection = nextSegment.getSegmentLength();
            } else {
                prefixBuffer = ByteBuffer.allocate(nextSegment.getPrefixSize());
            }
        } else {
            // we're done!
            prefixBuffer = ByteBuffer.allocate(0);
            bytesRemainingInSection = 1;
            segments = null;
            processMessageContents();
        }
    }

    private MessageDefinition findMessageDefinitionOf(int lastByteRead) {
        if (lastByteRead < 1) {
            return ControlMessage.BATCH_START;
        } else {
            return DefaultMessageDefinitions.fromTypeByte((byte) lastByteRead);
        }
    }

    private void processMessageContents() {
        final byte[] message = bytes.toByteArray();
        bytes.reset();
        if (currentBatch == null) {
            if (ControlMessage.isControlMessage(message[0])) {
                ByteBuffer size = ByteBuffer.allocate(4);
                size.put(message, 1, 2);
                size.flip();
                int batchSize = size.getShort();
                currentBatch = new ByteArrayInputStream[batchSize & 0xFFFF];
            } else {
                currentBatch = new ByteArrayInputStream[]{new ByteArrayInputStream(message)};
                readyToRead.offer(currentBatch);
                currentBatch = null;
            }
        } else {
            boolean written = false;
            for (int i=0;i<currentBatch.length&&!written;i++) {
                if (currentBatch[i] == null) {
                    currentBatch[i] = new ByteArrayInputStream(message);
                    written = true;
                }
            }
            if (currentBatch[currentBatch.length - 1] != null) {
                readyToRead.offer(currentBatch);
                currentBatch = null;
            }
        }
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    public InputStream getByteInputStream() {
        return new SequenceInputStream(new ArrayEnumeration(readyToRead.poll()));
    }

    private class ArrayEnumeration implements Enumeration<InputStream> {

        private final ByteArrayInputStream[] items;
        private int index;

        public ArrayEnumeration(ByteArrayInputStream[] items) {
            this.items = items;
            this.index = 0;
        }

        @Override
        public boolean hasMoreElements() {
            return items != null && items.length > index;
        }

        @Override
        public InputStream nextElement() {
            return items[index++];
        }
    }
}
