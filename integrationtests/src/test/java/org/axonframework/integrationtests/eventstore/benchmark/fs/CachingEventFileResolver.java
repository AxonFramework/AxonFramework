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

package org.axonframework.integrationtests.eventstore.benchmark.fs;

import org.axonframework.eventstore.fs.EventFileResolver;
import org.axonframework.eventstore.fs.SimpleEventFileResolver;
import org.springframework.beans.factory.DisposableBean;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * @author Allard Buijze
 */
public class CachingEventFileResolver implements EventFileResolver, DisposableBean {

    private final EventFileResolver delegate;
    private final ConcurrentMap<String, OutputStreamWrapper> openFiles = new ConcurrentSkipListMap<>();

    public CachingEventFileResolver(File baseDir) {
        this.delegate = new SimpleEventFileResolver(baseDir);
    }

    @Override
    public OutputStream openEventFileForWriting(String aggregateIdentifier)
            throws IOException {
        if (!openFiles.containsKey(aggregateIdentifier)) {
            OutputStream out = delegate.openEventFileForWriting(aggregateIdentifier);
            openFiles.putIfAbsent(aggregateIdentifier, new OutputStreamWrapper(out));
        }
        return openFiles.get(aggregateIdentifier);
    }

    @Override
    public OutputStream openSnapshotFileForWriting(String aggregateIdentifier)
            throws IOException {
        return delegate.openSnapshotFileForWriting(aggregateIdentifier);
    }

    @Override
    public InputStream openEventFileForReading(String aggregateIdentifier)
            throws IOException {
        return delegate.openEventFileForReading(aggregateIdentifier);
    }

    @Override
    public InputStream openSnapshotFileForReading(String aggregateIdentifier)
            throws IOException {
        return delegate.openSnapshotFileForReading(aggregateIdentifier);
    }

    @Override
    public boolean eventFileExists(String aggregateIdentifier) throws IOException {
        return openFiles.containsKey(aggregateIdentifier) || delegate.eventFileExists(aggregateIdentifier);
    }

    @Override
    public boolean snapshotFileExists(String aggregateIdentifier) throws IOException {
        return delegate.eventFileExists(aggregateIdentifier);
    }

    @Override
    public void destroy() throws Exception {
        for (OutputStreamWrapper openFile : openFiles.values()) {
            openFile.getDelegate().close();
        }
    }

    private static class OutputStreamWrapper extends OutputStream {

        private final OutputStream out;

        public OutputStreamWrapper(OutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            out.flush();
        }

        public OutputStream getDelegate() {
            return out;
        }
    }
}
