package com.fongmi.android.tv.gson;

import com.fongmi.android.tv.bean.Url;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class UrlAdapter implements JsonDeserializer<Url> {

    @Override
    public Url deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonArray()) return convert(json.getAsJsonArray());
        if (json.isJsonObject()) return Url.objectFrom(json);
        return json.isJsonPrimitive() ? Url.create().add(json.getAsString()) : Url.create();
    }

    private Url convert(JsonArray array) {
        Url url = Url.create();
        for (int i = 0; i + 1 < array.size(); i += 2) {
            JsonElement n = array.get(i), v = array.get(i + 1);
            if (n.isJsonPrimitive() && v.isJsonPrimitive()) url.add(n.getAsString(), v.getAsString());
        }
        return url;
    }
}
