/*
 * Copyright (c) 2010-2011. Axon Framework
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
import com.mongodb.DBObject;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class OrTest {

    @Test
    public void testOrOperator() {
        MongoCriteria criteria1 = new StubMongoCriteria(new BasicDBObject("a1", "b"));
        MongoCriteria criteria2 = new StubMongoCriteria(new BasicDBObject("a2", "b"));
        DBObject dbObject = new Or(criteria1, criteria2).asMongoObject();
        String actual = dbObject.toString();
        assertEquals("{ \"$or\" : [ " + criteria1.toString() + " , " + criteria2.toString() + "]}", actual);
    }
}
