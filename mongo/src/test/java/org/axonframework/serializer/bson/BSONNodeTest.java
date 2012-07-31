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

package org.axonframework.serializer.bson;

import org.junit.*;

import static org.junit.Assert.assertEquals;

/**
 * @author Allard Buijze
 */
public class BSONNodeTest {

    @Test
    public void testNodeStructure() {
        BSONNode node = new BSONNode("root");
        BSONNode child = node.addChildNode("child");
        child.setValue("childValue");
        child.setAttribute("attr", "val");
        node.addChildNode("valueOnly").setValue("onlyValue");
        node.addChildNode("singleChildOwner").addChildNode("grandChild").setValue("value");

        BSONNode parsedNode = BSONNode.fromDBObject(node.asDBObject());

        assertEquals(node.asDBObject().toString(), parsedNode.asDBObject().toString());
    }

}
