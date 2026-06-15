package com.litematicasearcher.client.api;

import com.google.gson.JsonObject;

public record RedenAttachment(
		String name,
		String url,
		long size,
		String description
) {
	public static RedenAttachment fromJson(JsonObject object) {
		return new RedenAttachment(
				RedenJson.string(object, "name"),
				RedenJson.string(object, "url"),
				RedenJson.longValue(object, "size"),
				RedenJson.string(object, "description")
		);
	}
}
