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
