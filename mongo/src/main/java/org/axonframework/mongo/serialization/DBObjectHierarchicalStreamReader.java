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

package org.axonframework.mongo.serialization;

import com.mongodb.DBObject;
import com.thoughtworks.xstream.converters.ErrorWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * HierarchicalStreamReader implementation that reads from a Mongo {@link DBObject} structure that has been created
 * using the {@link DBObjectHierarchicalStreamWriter}.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DBObjectHierarchicalStreamReader implements HierarchicalStreamReader {

    private final Deque<BSONNode> itemStack = new ArrayDeque<>();
    private final Deque<Iterator<BSONNode>> childrenStack = new ArrayDeque<>();

    /**
     * Initialize the reader to read the structure of the given {@code root} DBObject.
     *
     * @param root the root object containing the serialized structure
     */
    public DBObjectHierarchicalStreamReader(DBObject root) {
        BSONNode rootNode = BSONNode.fromDBObject(root);
        itemStack.push(rootNode);
        childrenStack.push(rootNode.children());
    }

    @Override
    public boolean hasMoreChildren() {
        return childrenStack.peek().hasNext();
    }

    @Override
    public void moveDown() {
        BSONNode currentNode = childrenStack.peek().next();
        itemStack.push(currentNode);
        childrenStack.push(currentNode.children());
    }

    @Override
    public void moveUp() {
        itemStack.pop();
        childrenStack.pop();
    }

    @Override
    public String getNodeName() {
        return itemStack.peek().getName();
    }

    @Override
    public String getValue() {
        return itemStack.peek().getValue();
    }

    @Override
    public String getAttribute(String name) {
        return itemStack.peek().getAttribute(name);
    }

    @Override
    public String getAttribute(int index) {
        throw new UnsupportedOperationException("Index based attributes not supported, yet");
    }

    @Override
    public int getAttributeCount() {
        return itemStack.peek().attributes().size();
    }

    @Override
    public String getAttributeName(int index) {
        throw new UnsupportedOperationException("Index based attributes not supported, yet");
    }

    @Override
    public Iterator getAttributeNames() {
        return itemStack.peek().attributes().keySet().iterator();
    }

    @Override
    public void appendErrors(ErrorWriter errorWriter) {
    }

    @Override
    public void close() {
    }

    @Override
    public HierarchicalStreamReader underlyingReader() {
        return this;
    }
}
