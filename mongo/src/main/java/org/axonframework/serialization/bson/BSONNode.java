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

package org.axonframework.serialization.bson;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.axonframework.common.Assert;

import java.util.*;

/**
 * Represents a node in a BSON structure. This class provides for an XML like abstraction around BSON for use by the
 * XStream Serializer.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class BSONNode {

    private static final String ATTRIBUTE_PREFIX = "attr_";
    private static final String VALUE_KEY = "_value";

    private final List<BSONNode> childNodes = new ArrayList<>();
    private String value;
    private String encodedName;

    /**
     * Creates a node with given "code" name. Generally, this constructor is used for the root node. For child nodes,
     * see the convenience method {@link #addChildNode(String)}.
     * <p/>
     * Note that the given <code>name</code> is encoded in a BSON compatible way. That means that periods (".") are
     * replaced by forward slashes "/", and any slashes are prefixes with additional slash. For example, the String
     * "some.period/slash" would become "some/period//slash". This is only imporant when querying BSON structures
     * directly.
     *
     * @param name The name of this node
     */
    public BSONNode(String name) {
        this.encodedName = encode(name);
    }

    /**
     * Constructrs a BSONNode structure from the given DBObject structure. The node returned is the root node.
     *
     * @param node The root DBObject node containing the BSON Structure
     * @return The BSONNode representing the root of the BSON structure
     */
    public static BSONNode fromDBObject(DBObject node) {
        if (node.keySet().size() != 1) {
            throw new IllegalArgumentException("Given node should have exactly one attribute");
        }
        String rootName = node.keySet().iterator().next();
        BSONNode rootNode = new BSONNode(decode(rootName));
        Object rootContents = node.get(rootName);
        if (rootContents instanceof List) {
            for (Object childElement : (List) rootContents) {
                if (childElement instanceof DBObject) {
                	DBObject dbChild = (DBObject) childElement;
                	if (dbChild.containsField(VALUE_KEY)) {
                		rootNode.setValue((String) dbChild.get(VALUE_KEY));
                	} else {
                		rootNode.addChildNode(fromDBObject(dbChild));
                	}
                }
            }
        } else if (rootContents instanceof DBObject) {
            rootNode.addChildNode(fromDBObject((DBObject) rootContents));
        } else if (rootContents instanceof String) {
            rootNode.setValue((String) rootContents);
        } else {
            throw new IllegalArgumentException(
                    "Node in " + rootName + " contains child " + rootContents + " which cannot be parsed");
        }
        return rootNode;
    }

    private void addChildNode(BSONNode bsonNode) {
        this.childNodes.add(bsonNode);
    }

    /**
     * Returns the current BSON structure as DBObject.
     *
     * @return the current BSON structure as DBObject
     */
    public DBObject asDBObject() {
        if (childNodes.isEmpty() && value != null) {
            // only a value
            return new BasicDBObject(encodedName, value);
        } else if (childNodes.size() == 1 && value == null) {
            return new BasicDBObject(encodedName, childNodes.get(0).asDBObject());
        } else {
            BasicDBList subNodes = new BasicDBList();
            BasicDBObject thisNode = new BasicDBObject(encodedName, subNodes);
            if (value != null) {
                subNodes.add(new BasicDBObject(VALUE_KEY, value));
            }
            for (BSONNode childNode : childNodes) {
                subNodes.add(childNode.asDBObject());
            }
            return thisNode;
        }
    }

    /**
     * Sets the value of this node. Note that a node can contain either a value, or child nodes
     *
     * @param value The value to set for this node
     */
    public void setValue(String value) {
        Assert.isFalse(children().hasNext(), "A child node was already present. "
                + "A node cannot contain a value as well as child nodes.");
        this.value = value;
    }

    /**
     * Sets an attribute to this node. Since JSON (and BSON) do not have the notion of attributes, these are modelled
     * as child nodes with their name prefixed with "attr_". Attributes are represented as nodes that <em>always</em>
     * exclusively contain a value.
     *
     * @param attributeName  The name of the attribute to add
     * @param attributeValue The value of the attribute
     */
    public void setAttribute(String attributeName, String attributeValue) {
    	addChild(ATTRIBUTE_PREFIX + attributeName, attributeValue);
    }

    /**
     * Adds a child node to the current node. Note that the name of a child node must not be <code>null</code> and may
     * not start with "attr_", in order to differentiate between child nodes and attributes.
     *
     * @param name The name of the child node
     * @return A BSONNode representing the newly created child node.
     */
    public BSONNode addChildNode(String name) {
        Assert.isTrue(value == null, "A value was already present."
                + "A node cannot contain a value as well as child nodes");
        Assert.notNull(name, "Node name must not be null");
        Assert.isFalse(name.startsWith(ATTRIBUTE_PREFIX), "Node names may not start with '" + ATTRIBUTE_PREFIX + "'");

        return addChild(name, null);
    }
    
    private BSONNode addChild(String name, String nodeValue) {
        BSONNode childNode = new BSONNode(name);
        childNode.setValue(nodeValue);
        this.childNodes.add(childNode);
        return childNode;
    }

    /**
     * Returns an iterator providing access to the child nodes of the current node. Changes to the node structure made
     * after this iterator is returned may not be reflected in that iterator.
     *
     * @return an iterator providing access to the child nodes of the current node
     */
    public Iterator<BSONNode> children() {
        List<BSONNode> children = new ArrayList<>();
        for (BSONNode child : childNodes) {
            if (!child.encodedName.startsWith(ATTRIBUTE_PREFIX)) {
                children.add(child);
            }
        }
        return children.iterator();
    }

    /**
     * Returns a map containing the attributes of the current node. Changes to the node's attributes made
     * after this iterator is returned may not be reflected in that iterator.
     *
     * @return a Map containing the attributes of the current node
     */
    public Map<String, String> attributes() {
        Map<String, String> attrs = new HashMap<>();
        for (BSONNode child : childNodes) {
            if (!VALUE_KEY.equals(child.encodedName) && child.encodedName.startsWith(ATTRIBUTE_PREFIX)) {
                attrs.put(child.encodedName, child.value);
            }
        }
        return attrs;
    }

    /**
     * Returns the value of the attribute with given <code>name</code>.
     *
     * @param name The name of the attribute to get the value for
     * @return the value of the attribute, or <code>null</code> if the attribute was not found
     */
    public String getAttribute(String name) {
        String attr = ATTRIBUTE_PREFIX + name;
        return getChildValue(attr);
    }
    
    private String getChildValue(String name) {
        for (BSONNode node : childNodes) {
            if (name.equals(node.encodedName)) {
                return node.value;
            }
        }
        return null;
    }

    /**
     * Returns the value of the current node
     *
     * @return the value of the current node, or <code>null</code> if this node has no value.
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the name of the current node
     *
     * @return the name of the current node
     */
    public String getName() {
        return decode(encodedName);
    }

    private static String encode(String name) {
        return name.replaceAll("\\/", "\\/\\/").replaceAll("\\.", "/");
    }

    private static String decode(String name) {
        return name.replaceAll("\\/([^/]])?", ".$1").replaceAll("\\/\\/", "/");
    }
}
