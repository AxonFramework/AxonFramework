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

package org.axonframework.test;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.repository.Repository;

import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public abstract class Fixtures {

    private static ThreadLocal<GivenWhenThenTestFixture> currentFixture = new ThreadLocal<GivenWhenThenTestFixture>();

    public static FixtureConfiguration givenWhenThenFixture() {
        GivenWhenThenTestFixture fixture = new GivenWhenThenTestFixture();
        currentFixture.set(fixture);
        return fixture;
    }

    @SuppressWarnings({"unchecked"})
    public static <T extends AggregateRoot> Repository<T> genericRepository(Class<T> aggregateType) {
        GivenWhenThenTestFixture fixture = getCurrentFixture();
        if (fixture.getRepository() == null) {
            fixture.registerGenericRepository(aggregateType);
        }

        return (Repository<T>) fixture.getRepository();
    }

    @SuppressWarnings({"unchecked"})
    public static Repository<? extends AggregateRoot> getRepository() {
        return getCurrentFixture().getRepository();
    }

    public static UUID aggregateIdentifier() {
        return getCurrentFixture().getAggregateIdentifier();
    }

    private static GivenWhenThenTestFixture getCurrentFixture() {
        GivenWhenThenTestFixture fixture = currentFixture.get();
        assertNotNull("The fixture was not properly initialized. "
                + "You must create a fixture instance first, using givenWhenThenFixture()",
                      fixture);
        return fixture;
    }
}
