package com.litematicasearcher.client.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public record RedenSearchResponse(
		List<RedenMachine> machines,
		int offset,
		int limit,
		int estimatedTotalHits,
		int count,
		int processingTimeMs,
		String query,
		int downloads
) {
	public static RedenSearchResponse fromJson(JsonObject object) {
		List<RedenMachine> machines = new ArrayList<>();

		for (JsonElement element : RedenJson.array(object, "d")) {
			if (element.isJsonObject()) {
				machines.add(RedenMachine.fromJson(element.getAsJsonObject()));
			}
		}

		return new RedenSearchResponse(
				List.copyOf(machines),
				RedenJson.integer(object, "offset"),
				RedenJson.integer(object, "limit"),
				RedenJson.integer(object, "estimatedTotalHits"),
				RedenJson.integer(object, "count"),
				RedenJson.integer(object, "processingTimeMs"),
				RedenJson.string(object, "query"),
				RedenJson.integer(object, "downloads")
		);
	}
}
