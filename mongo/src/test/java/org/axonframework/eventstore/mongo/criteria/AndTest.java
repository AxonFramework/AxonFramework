/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.eventstore.mongo.criteria;

import com.mongodb.BasicDBObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class AndTest {

    @Test
    public void testAndOperator() {
        MongoCriteria criteria1 = new StubMongoCriteria(new BasicDBObject("a1", "b"));
        MongoCriteria criteria2 = new StubMongoCriteria(new BasicDBObject("a2", "b"));
        String actual = new And(criteria1, criteria2).asMongoObject().toString();
        assertEquals("{ \"$and\" : [ " + criteria1.toString() + " , " + criteria2.toString() + "]}", actual);
    }
}
