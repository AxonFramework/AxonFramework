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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface that allows basic access to InputStreams and appending OutputStreams to event logs for aggregates.
 * <p/>
 * The streams provided by these methods should be closed by the caller when it has finished using them.
 *
 * @author Allard Buijze
 * @since 0.5
 */
public interface EventFileResolver {

    /**
     * Provides an output stream to the (regular) events file for the aggregate with the given
     * <code>aggregateIdentifier</code>. Written bytes are appended to already existing information.
     * <p/>
     * The caller of this method is responsible for closing the output stream when all data has been written to it.
     *
     * @param aggregateIdentifier the identifier of the aggregate
     * @return an OutputStream that appends to the event log of of the given aggregate
     *
     * @throws java.io.IOException when an error occurs while opening a file
     */
    OutputStream openEventFileForWriting(String aggregateIdentifier) throws IOException;

    /**
     * Provides an output stream to the snapshot events file for the aggregate with the given
     * <code>aggregateIdentifier</code>. Written bytes are appended to already existing information.
     * <p/>
     * The caller of this method is responsible for closing the output stream when all data has been written to it.
     *
     * @param aggregateIdentifier the identifier of the aggregate
     * @return an OutputStream that appends to the snapshot event log of of the given aggregate
     *
     * @throws java.io.IOException when an error occurs while opening a file
     */
    OutputStream openSnapshotFileForWriting(String aggregateIdentifier) throws IOException;

    /**
     * Provides an input stream to the (regular) events file for the aggregate with the given
     * <code>aggregateIdentifier</code>.
     * <p/>
     * The caller of this method is responsible for closing the input stream when done reading from it.
     *
     * @param aggregateIdentifier the identifier of the aggregate
     * @return an InputStream that reads from the event log of of the given aggregate
     *
     * @throws java.io.IOException when an error occurs while opening a file
     */
    InputStream openEventFileForReading(String aggregateIdentifier) throws IOException;

    /**
     * Provides an input stream to the snapshot events file for the aggregate with the given
     * <code>aggregateIdentifier</code>.
     * <p/>
     * The caller of this method is responsible for closing the input stream when done reading from it.
     *
     * @param aggregateIdentifier the identifier of the aggregate
     * @return an InputStream that reads from the snapshot event log of of the given aggregate
     *
     * @throws java.io.IOException when an error occurs while opening a file
     */
    InputStream openSnapshotFileForReading(String aggregateIdentifier) throws IOException;

    /**
     * Indicates whether there is a file containing (regular) events for the given <code>aggregateIdentifier</code>.
     *
     * @param aggregateIdentifier the identifier of the aggregate
     * @return <code>true</code> if an event log exists for the aggregate, <code>false</code> otherwise.
     *
     * @throws IOException when an error occurs while reading from the FileSystem. The existence of the event file is
     *                     undetermined.
     */
    boolean eventFileExists(String aggregateIdentifier) throws IOException;

    /**
     * Indicates whether there is a file containing snapshot events for the given <code>aggregateIdentifier</code>.
     *
     * @param aggregateIdentifier the identifier of the aggregate
     * @return <code>true</code> if a snapshot event log exists for the aggregate, <code>false</code> otherwise.
     *
     * @throws IOException when an error occurs while reading from the FileSystem. The existence of the event file is
     *                     undetermined.
     */
    boolean snapshotFileExists(String aggregateIdentifier) throws IOException;
}
