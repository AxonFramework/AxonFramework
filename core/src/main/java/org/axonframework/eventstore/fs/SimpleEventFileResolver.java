/*
 * Copyright (c) 2010-2014. Axon Framework
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

package org.axonframework.eventstore.fs;

import org.axonframework.eventstore.EventStoreException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Very straightforward implementation of the EventFileResolver that stores files in a given base directory. Events of
 * a single aggregate are appended to a pair of files, one for regular events and one for snapshot events.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public class SimpleEventFileResolver implements EventFileResolver {

    /**
     * Describes the file extension used for files containing domain events.
     */
    public static final String FILE_EXTENSION_EVENTS = "events";
    /**
     * Describes the file extension used for files containing snapshot events.
     */
    public static final String FILE_EXTENSION_SNAPSHOTS = "snapshots";

    protected final File baseDir;

    /**
     * Initialize the SimpleEventFileResolver with the given <code>baseDir</code>.
     * <p/>
     * Note that the resource supplied must point to a folder and should contain a trailing slash. See {@link
     * org.springframework.core.io.FileSystemResource#FileSystemResource(String)}.
     *
     * @param baseDir The directory where event files are stored.
     */
    public SimpleEventFileResolver(File baseDir) {
        this.baseDir = baseDir;
    }

    private static String fsSafeIdentifier(Object id) {
        try {
            return URLEncoder.encode(id.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("System doesn't support UTF-8?", e);
        }
    }

    @Override
    public OutputStream openEventFileForWriting(String aggregateIdentifier)
            throws IOException {
        File eventFile = getEventsFile(aggregateIdentifier, FILE_EXTENSION_EVENTS);
        return new BufferedOutputStream(new FileOutputStream(eventFile, true));
    }

    @Override
    public OutputStream openSnapshotFileForWriting(String aggregateIdentifier)
            throws IOException {
        return new FileOutputStream(getEventsFile(aggregateIdentifier, FILE_EXTENSION_SNAPSHOTS), true);
    }

    @Override
    public InputStream openEventFileForReading(String identifier) throws IOException {
        return new FileInputStream(getEventsFile(identifier, FILE_EXTENSION_EVENTS));
    }

    @Override
    public InputStream openSnapshotFileForReading(String identifier) throws IOException {
        return new FileInputStream(getEventsFile(identifier, FILE_EXTENSION_SNAPSHOTS));
    }

    @Override
    public boolean eventFileExists(String identifier) throws IOException {
        return getEventsFile(identifier, FILE_EXTENSION_EVENTS).exists();
    }

    @Override
    public boolean snapshotFileExists(String identifier) throws IOException {
        return getEventsFile(identifier, FILE_EXTENSION_SNAPSHOTS).exists();
    }

    private File getEventsFile(String identifier, String extension) {
        return new File(getBaseDir(), fsSafeIdentifier(identifier) + "." + extension);
    }

    private File getBaseDir() {
        if (!baseDir.exists() && !baseDir.mkdirs() && !baseDir.exists()) {
            throw new EventStoreException("The given event store directory doesn't exist and could not be created");
        }
        return baseDir;
    }
}
