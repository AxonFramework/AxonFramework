package org.axonframework.serializer.bson;

import com.mongodb.DBObject;
import com.thoughtworks.xstream.converters.ErrorWriter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

/**
 * HierarchicalStreamReader implementation that reads from a Mongo {@link DBObject} structure that has been created
 * using the {@link DBObjectHierarchicalStreamWriter}.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class DBObjectHierarchicalStreamReader implements HierarchicalStreamReader {

    private static final String ATTRIBUTE_PREFIX = "_";
    private static final String VALUE_KEY = "_value";

    private final Stack<NameAwareObject> itemStack = new Stack<NameAwareObject>();
    private final Stack<Iterator<NameAwareObject>> childrenStack = new Stack<Iterator<NameAwareObject>>();
    private String nodeValue = null;

    /**
     * Initialize the reader to read the structure of the given <code>root</code> DBObject.
     *
     * @param root the root object containing the serialized structure
     */
    public DBObjectHierarchicalStreamReader(DBObject root) {
        String rootName = root.keySet().iterator().next();
        DBObject rootItem = (DBObject) root.get(rootName);
        itemStack.push(new NameAwareObject(rootName, rootItem));
        childrenStack.push(childNodes(rootItem).iterator());
    }

    @Override
    public boolean hasMoreChildren() {
        return nodeValue == null && childrenStack.peek().hasNext();
    }

    @Override
    public void moveDown() {
        NameAwareObject nextChildNode = childrenStack.peek().next();
        String nextChildName = nextChildNode.getName();
        Object nextChild = nextChildNode.dbObject;
        if (nextChild instanceof DBObject) {
            itemStack.push(new NameAwareObject(nextChildName, nextChild));
            childrenStack.push(childNodes((DBObject) nextChild).iterator());
            nodeValue = null;
        } else {
            itemStack.push(new NameAwareObject(nextChildName, nextChild.toString()));
            nodeValue = nextChild.toString();
            childrenStack.push(Collections.<NameAwareObject>emptyList().iterator());
        }
    }

    @Override
    public void moveUp() {
        itemStack.pop();
        childrenStack.pop();
        nodeValue = null;
    }

    @Override
    public String getNodeName() {
        return decode(itemStack.peek().getName());
    }

    private DBObject currentNode() {
        Object object = itemStack.peek().getDbObject();
        if (object instanceof DBObject) {
            return (DBObject) object;
        } else {
            return null;
        }
    }

    @Override
    public String getValue() {
        if (nodeValue != null) {
            return nodeValue;
        } else if (currentNode().containsField(VALUE_KEY)) {
            return (String) currentNode().get(VALUE_KEY);
        }
        return "";
    }

    @Override
    public String getAttribute(String name) {
        DBObject current = currentNode();
        if (current == null) {
            return null;
        }
        List<NameAwareObject> attributes = attributes(currentNode());
        for (NameAwareObject attribute : attributes) {
            if (encode(ATTRIBUTE_PREFIX + name).equals(attribute.getName())) {
                return (String) attribute.getDbObject();
            }
        }
        return null;
    }

    @Override
    public String getAttribute(int index) {
        DBObject current = currentNode();
        if (current == null) {
            return null;
        }
        return (String) attributes(current).get(index).getDbObject();
    }

    @Override
    public int getAttributeCount() {
        return attributes(currentNode()).size();
    }

    @Override
    public String getAttributeName(int index) {
        DBObject current = currentNode();
        return decode(attributes(current).get(index).getName());
    }

    @Override
    public Iterator getAttributeNames() {
        List<NameAwareObject> attributes = attributes(currentNode());
        List<String> attributeNames = new ArrayList<String>();
        for (NameAwareObject attribute : attributes) {
            attributeNames.add(decode(attribute.getName()));
        }
        return attributeNames.iterator();
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

    private List<NameAwareObject> childNodes(DBObject object) {
        List<NameAwareObject> fields = fieldsOf(object);
        List<NameAwareObject> childNodes = new ArrayList<NameAwareObject>();
        for (NameAwareObject field : fields) {
            if (!field.getName().startsWith(ATTRIBUTE_PREFIX)) {
                childNodes.add(field);
            }
        }
        return childNodes;
    }

    private List<NameAwareObject> attributes(DBObject object) {
        List<NameAwareObject> fields = fieldsOf(object);
        List<NameAwareObject> attributes = new ArrayList<NameAwareObject>();
        for (NameAwareObject field : fields) {
            if (field.getName().startsWith(ATTRIBUTE_PREFIX) && !VALUE_KEY.equals(field.getName())) {
                attributes.add(field);
            }
        }
        return attributes;
    }

    @SuppressWarnings("unchecked")
    private List<NameAwareObject> fieldsOf(DBObject object) {
        List<NameAwareObject> childNodes = new ArrayList<NameAwareObject>();
        if (object instanceof List) {
            for (DBObject item : (List<DBObject>) object) {
                childNodes.addAll(fieldsOf(item));
            }
        } else if (object != null) {
            for (String fieldName : object.keySet()) {
                Object field = object.get(fieldName);
                if (field instanceof List) {
                    List<DBObject> fieldAsList = (List<DBObject>) field;
                    for (DBObject fieldValue : fieldAsList) {
                        childNodes.add(new NameAwareObject(fieldName, fieldValue));
                    }
                } else {
                    childNodes.add(new NameAwareObject(fieldName, field));
                }
            }
        }
        return childNodes;
    }

    private String decode(String name) {
        return name.replaceAll("\\/", ".");
    }

    private String encode(String name) {
        return name.replaceAll("\\.", "/");
    }

    private class NameAwareObject {

        private final String name;
        private final Object dbObject;

        private NameAwareObject(String name, Object dbObject) {
            this.name = name;
            this.dbObject = dbObject;
        }

        public String getName() {
            return name;
        }

        public Object getDbObject() {
            return dbObject;
        }
    }
}
