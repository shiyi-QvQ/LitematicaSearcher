package com.litematicasearcher.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

final class RedenJson {
	private RedenJson() {
	}

	static JsonArray array(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	static JsonObject object(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
	}

	static String string(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && !element.isJsonNull() ? element.getAsString() : "";
	}

	static boolean bool(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && !element.isJsonNull() && element.getAsBoolean();
	}

	static int integer(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && !element.isJsonNull() ? element.getAsInt() : 0;
	}

	static long longValue(JsonObject object, String key) {
		JsonElement element = object.get(key);
		return element != null && !element.isJsonNull() ? element.getAsLong() : 0L;
	}

	static List<String> stringList(JsonObject object, String key) {
		List<String> values = new ArrayList<>();

		for (JsonElement element : array(object, key)) {
			if (element != null && !element.isJsonNull()) {
				values.add(element.getAsString());
			}
		}

		return List.copyOf(values);
	}
}
