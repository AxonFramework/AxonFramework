/*
 * Copyright (c) 2010. Gridshore
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

import org.axonframework.domain.AggregateIdentifier;
import org.axonframework.domain.AggregateIdentifierFactory;
import org.axonframework.eventstore.fs.FileSystemEventStore;
import org.axonframework.integrationtests.eventstore.benchmark.AbstractEventStoreBenchmark;

/**
 * @author Jettro Coenradie
 */
public class FileSystemEventStoreBenchMark extends AbstractEventStoreBenchmark {
    private FileSystemEventStore fileSystemEventStore;

    public FileSystemEventStoreBenchMark(FileSystemEventStore fileSystemEventStore) {
        this.fileSystemEventStore = fileSystemEventStore;
    }

    public static void main(String[] args) throws Exception {
        AbstractEventStoreBenchmark benchmark = prepareBenchMark("META-INF/spring/benchmark-filesystem-context.xml");
        benchmark.startBenchMark();
    }

    @Override
    protected void prepareEventStore() {
    }

    @Override
    protected Runnable getRunnableInstance() {
        return new FileSystemBenchmark();
    }

    private class FileSystemBenchmark implements Runnable {

        @Override
        public void run() {
            final AggregateIdentifier aggregateId = AggregateIdentifierFactory.randomIdentifier();
            int eventSequence = 0;
            for (int t = 0; t < getTransactionCount(); t++) {
                eventSequence = saveAndLoadLargeNumberOfEvents(aggregateId, fileSystemEventStore, eventSequence);
            }
            System.out.println("Sequence : " + eventSequence);
        }
    }

}
