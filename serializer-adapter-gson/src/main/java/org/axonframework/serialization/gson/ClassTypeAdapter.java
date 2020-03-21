package org.axonframework.serialization.gson;

import com.google.gson.*;

import java.lang.reflect.Type;

class ClassTypeAdapter implements JsonSerializer<Class<Integer>>, JsonDeserializer<Class> {

    @Override
    public JsonElement serialize(Class src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.getName());
    }

    @Override
    public Class deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            return Class.forName(json.getAsString());
        } catch (ClassNotFoundException e) {
            throw new JsonParseException(e);
        }
    }
}
