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

import com.mongodb.DBObject;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.axonframework.common.Assert;

import java.util.Stack;

/**
 * HierarchicalStreamWriter implementation that writes objects into a MongoDB DBObject structure. Use the {@link
 * DBObjectHierarchicalStreamReader} to read the object back.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DBObjectHierarchicalStreamWriter implements ExtendedHierarchicalStreamWriter {

    private final Stack<BSONNode> itemStack = new Stack<>();
    private final DBObject root;

    /**
     * Initialize the writer to write the object structure to the given <code>root</code> DBObject.
     * <p/>
     * Note that the given <code>root</code> DBObject must not contain any data yet.
     *
     * @param root The root DBObject to which the serialized structure will be added. Must not contain any data.
     */
    public DBObjectHierarchicalStreamWriter(DBObject root) {
        Assert.isTrue(root.keySet().isEmpty(), "The given root object must be empty.");
        this.root = root;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void startNode(String name) {
        if (itemStack.empty()) {
            itemStack.push(new BSONNode(name));
        } else {
            itemStack.push(itemStack.peek().addChildNode(name));
        }
    }

    @Override
    public void addAttribute(String name, String value) {
        itemStack.peek().setAttribute(name, value);
    }

    @Override
    public void setValue(String text) {
        itemStack.peek().setValue(text);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void endNode() {
        BSONNode closingElement = itemStack.pop();
        if (itemStack.isEmpty()) {
            // we've popped the last one, so we're done
            root.putAll(closingElement.asDBObject());
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public HierarchicalStreamWriter underlyingWriter() {
        return this;
    }

    @Override
    public void startNode(String name, Class clazz) {
        startNode(name);
    }
}
