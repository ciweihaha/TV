package com.fongmi.android.tv.gson;

import com.fongmi.android.tv.bean.Filter;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FilterAdapter implements JsonDeserializer<LinkedHashMap<String, List<Filter>>> {

    @Override
    public LinkedHashMap<String, List<Filter>> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        LinkedHashMap<String, List<Filter>> filterMap = new LinkedHashMap<>();
        if (!json.isJsonObject()) return filterMap;
        JsonObject filters = json.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : filters.entrySet()) {
            List<Filter> items = new ArrayList<>();
            JsonElement element = entry.getValue();
            if (element.isJsonObject()) {
                try {
                    Filter filter = Filter.objectFrom(element);
                    if (filter != null) items.add(filter.check().trans());
                } catch (Exception ignored) {
                }
            } else if (element.isJsonArray()) {
                for (JsonElement item : element.getAsJsonArray()) {
                    try {
                        Filter filter = Filter.objectFrom(item);
                        if (filter != null) items.add(filter.check().trans());
                    } catch (Exception ignored) {
                    }
                }
            }
            filterMap.put(entry.getKey(), items);
        }
        return filterMap;
    }
}
