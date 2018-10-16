/*
 * Copyright (c) 2018. AxonIQ
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

package org.axonframework.axonserver.connector.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Spliterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Converts the push interface for QueryResponses from AxonServer to a pull interface to be used by Axon Framework
 * Uses a queue to cache messages
 * @author Marc Gathier
 */
public class QueueBackedSpliterator<R> implements Spliterator<R>{
    private final static Logger logger = LoggerFactory.getLogger(QueueBackedSpliterator.class);
    private final long myTimeOut;
    private final BlockingQueue<WrappedElement<R>> blockingQueue = new LinkedBlockingQueue<>();

    class WrappedElement<W> {
        private final W wrapped;
        private final boolean stop;
        private final Throwable exception;

        WrappedElement(W wrapped) {
            this.wrapped = wrapped;
            this.stop = false;
            this.exception = null;
        }
        WrappedElement(boolean stop, Throwable exception) {
            this.wrapped = null;
            this.stop = stop;
            this.exception = exception;
        }
    }

    public QueueBackedSpliterator(long timeout, TimeUnit timeUnit ) {
        myTimeOut = System.currentTimeMillis() + timeUnit.toMillis(timeout);
    }

    @Override
    public boolean tryAdvance(Consumer<? super R> action) {
        WrappedElement<R> element = null;
        try {
            long remaining = myTimeOut - System.currentTimeMillis();
            if(remaining > 0) {
                element = blockingQueue.poll(myTimeOut - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
                if( element != null) {
                    if(  element.stop ) return false;
                    if ( element.wrapped != null)
                        action.accept(element.wrapped);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted tryAdvance", e);
            return false;

        }
        return element != null;
    }

    @Override
    public Spliterator<R> trySplit() {
        return null;
    }

    @Override
    public long estimateSize() {
        return 0;
    }

    @Override
    public int characteristics() {
        return 0;
    }

    public void put(R object) {
        try {
            blockingQueue.put(new WrappedElement<>(object));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted put", e);
            throw new RuntimeException(e);
        }
    }

    public void cancel( Throwable t) {
        try {
            blockingQueue.put(new WrappedElement<>(true, t));
        } catch (InterruptedException e) {
            logger.warn("Interrupted cancel", e);
        }

    }

}
