/*
 * Copyright (c) 2010. Axon Framework
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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.eventstore.EventStoreException;
import org.springframework.core.io.Resource;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Very straightforward implementation of the EventFileResolver that stores files in a directory structure underneath a
 * given base directory. Events of a single aggregate are appended to a pair of files, one for regular events and one
 * for snapshot events. Directories are used to separate files for different aggregate types.
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

    private final Resource baseDir;

    /**
     * Initialize the SimpleEventFileResolver with the given <code>baseDir</code>.
     * <p/>
     * Note that the resource supplied must point to a folder and should contain a trailing slash. See {@link
     * org.springframework.core.io.FileSystemResource#FileSystemResource(String)}.
     *
     * @param baseDir The directory where event files are stored.
     */
    public SimpleEventFileResolver(Resource baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public OutputStream openEventFileForWriting(String type, AggregateIdentifier aggregateIdentifier)
            throws IOException {
        File eventFile = getEventsFile(type, aggregateIdentifier, FILE_EXTENSION_EVENTS).getFile();
        return new BufferedOutputStream(new FileOutputStream(eventFile, true));
    }

    @Override
    public OutputStream openSnapshotFileForWriting(String type, AggregateIdentifier aggregateIdentifier)
            throws IOException {
        return new FileOutputStream(getEventsFile(type, aggregateIdentifier, FILE_EXTENSION_SNAPSHOTS).getFile(), true);
    }

    @Override
    public InputStream openEventFileForReading(String type, AggregateIdentifier identifier) throws IOException {
        return getEventsFile(type, identifier, FILE_EXTENSION_EVENTS).getInputStream();
    }

    @Override
    public InputStream openSnapshotFileForReading(String type, AggregateIdentifier identifier) throws IOException {
        return getEventsFile(type, identifier, FILE_EXTENSION_SNAPSHOTS).getInputStream();
    }

    @Override
    public boolean eventFileExists(String type, AggregateIdentifier identifier) throws IOException {
        return getEventsFile(type, identifier, FILE_EXTENSION_EVENTS).exists();
    }

    @Override
    public boolean snapshotFileExists(String type, AggregateIdentifier identifier) throws IOException {
        return getEventsFile(type, identifier, FILE_EXTENSION_SNAPSHOTS).exists();
    }

    private Resource getEventsFile(String type, AggregateIdentifier identifier, String extension) throws IOException {
        return getBaseDirForType(type).createRelative(identifier + "." + extension);
    }

    private Resource getBaseDirForType(String type) throws IOException {

        Resource typeSpecificDir = baseDir.createRelative("/" + type + "/");
        if (!typeSpecificDir.exists() && !typeSpecificDir.getFile().mkdirs()) {
            throw new EventStoreException(
                    "The given event store directory doesn't exist and could not be created");
        }
        return typeSpecificDir;
    }
}
