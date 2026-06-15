package com.litematicasearcher.client.api;

import com.google.gson.JsonObject;

public record RedenTag(
		String tag,
		String name,
		String description
) {
	public static RedenTag fromJson(JsonObject object) {
		return new RedenTag(
				RedenJson.string(object, "tag"),
				RedenJson.string(object, "name"),
				RedenJson.string(object, "description")
		);
	}
}
