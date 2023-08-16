/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.aggregate;

import java.util.Arrays;

/**
 * @author Allard Buijze
 */
public class MyEvent {

    private final Integer someValue;
    private final byte[] someBytes;
    private final Object aggregateIdentifier;

    public MyEvent(Object aggregateIdentifier, Integer someValue) {
        this(aggregateIdentifier, someValue, new byte[]{});
    }

    public MyEvent(Object aggregateIdentifier, Integer someValue, byte[] someBytes) {
        this.aggregateIdentifier = aggregateIdentifier;
        this.someValue = someValue;
        this.someBytes = someBytes;
    }

    public Integer getSomeValue() {
        return someValue;
    }

    public byte[] getSomeBytes() {
        return someBytes;
    }

    public Object getAggregateIdentifier() {
        return aggregateIdentifier;
    }

    @Override
    public String toString() {
        return "MyEvent{" +
                "someValue=" + someValue +
                ", someBytes=" + Arrays.toString(someBytes) +
                ", aggregateIdentifier=" + aggregateIdentifier +
                '}';
    }
}
