package org.axonframework.serializer.bson;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.axonframework.common.Assert;

import java.util.List;
import java.util.Stack;

/**
 * HierarchicalStreamWriter implementation that writes objects into a MongoDB DBObject structure. Use the {@link
 * DBObjectHierarchicalStreamReader} to read the object back.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DBObjectHierarchicalStreamWriter implements ExtendedHierarchicalStreamWriter {

    private static final String ATTRIBUTE_PREFIX = "_";
    private static final String VALUE_KEY = "_value";
    private final Stack<DBObject> itemStack = new Stack<DBObject>();
    private final Stack<String> nameStack = new Stack<String>();

    /**
     * Initialize the writer to write the object structure to the given <code>root</code> DBObject.
     * <p/>
     * Note that the given <code>root</code> DBObject must not contain any data yet.
     *
     * @param root The root DBObject to which the serialized structure will be added. Must not contain any data.
     */
    public DBObjectHierarchicalStreamWriter(DBObject root) {
        Assert.isTrue(root.keySet().isEmpty(), "The given root object must be empty.");
        itemStack.push(root);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void startNode(String name) {
        String encodedName = encode(name);
        DBObject current = itemStack.peek();
        List<DBObject> currentChildren;
        if (!current.containsField(encodedName)) {
            current.put(encodedName, new BasicDBList());
        }
        currentChildren = (List<DBObject>) current.get(encodedName);
        BasicDBObject node = new BasicDBObject();
        currentChildren.add(node);
        itemStack.push(node);
        nameStack.push(encodedName);
    }

    @Override
    public void addAttribute(String name, String value) {
        itemStack.peek().put(ATTRIBUTE_PREFIX + encode(name), value);
    }

    @Override
    public void setValue(String text) {
        itemStack.peek().put(VALUE_KEY, text);
    }

    @Override
    public void endNode() {
        DBObject dbObject = itemStack.pop();
        String name = nameStack.pop();
        if (dbObject.keySet().size() == 1 && dbObject.containsField(VALUE_KEY)) {
            itemStack.peek().put(name, dbObject.get(VALUE_KEY));
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

    private String encode(String name) {
        return name.replaceAll("\\.", "/");
    }
}
