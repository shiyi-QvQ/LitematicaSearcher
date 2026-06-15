package com.litematicasearcher.client.api;

import com.google.gson.JsonObject;

public record RedenAuthor(
		int id,
		String username,
		String avatarUrl,
		boolean staff
) {
	public static RedenAuthor fromJson(JsonObject object) {
		return new RedenAuthor(
				RedenJson.integer(object, "id"),
				RedenJson.string(object, "username"),
				RedenJson.string(object, "avatarUrl"),
				RedenJson.bool(object, "isStaff")
		);
	}
}
