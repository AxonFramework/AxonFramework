package org.axonframework.serializer.bson;

import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import org.axonframework.serializer.AbstractContentTypeConverter;

/**
 * ContentTypeConverter implementation that converts a String containing its Binary JSON representation into a DBObject
 * structure.
 *
 * @author Allard Buijze
 * @since 2.0
 */
public class StringToDBObjectContentTypeConverter extends AbstractContentTypeConverter<String, DBObject> {

    @Override
    public Class<String> expectedSourceType() {
        return String.class;
    }

    @Override
    public Class<DBObject> targetType() {
        return DBObject.class;
    }

    @Override
    public DBObject convert(String original) {
        return (DBObject) JSON.parse(original);
    }
}
