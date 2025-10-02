package com.doubleclick.wadii.notification;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class JsonObjectConverter implements AttributeConverter<JsonObject, String> {
    private final Gson gson = new Gson();

    @Override
    public String convertToDatabaseColumn(JsonObject jsonObject) {
        return jsonObject != null ? gson.toJson(jsonObject) : null;
    }

    @Override
    public JsonObject convertToEntityAttribute(String json) {
        return json != null ? gson.fromJson(json, JsonObject.class) : null;
    }
}
