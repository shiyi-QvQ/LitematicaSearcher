package com.litematicasearcher.client.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RedenMachine(
		String type,
		String name,
		String key,
		boolean hasX,
		boolean hasY,
		boolean hasZ,
		Map<String, List<String>> conditions,
		String link,
		String summary,
		String description,
		long updatedAt,
		RedenAuthor author,
		String imageUrl,
		String thumbnailUrl,
		List<RedenAttachment> attachments,
		int downloads,
		List<String> images,
		List<RedenTag> featureTags,
		List<String> versions,
		boolean original,
		int upVotes,
		int downVotes,
		String language
) {
	public static final String TYPE_LITEMATICA_GEN = "LitematicaGen";
	public static final String TYPE_LITEMATICA_SHARE = "LitematicaShare";

	public boolean isGenerationType() {
		return TYPE_LITEMATICA_GEN.equals(type);
	}

	public boolean isShareType() {
		return TYPE_LITEMATICA_SHARE.equals(type);
	}

	public List<String> conditionsFor(String axis) {
		return conditions.getOrDefault(axis, List.of());
	}

	public static RedenMachine fromJson(JsonObject object) {
		return new RedenMachine(
				RedenJson.string(object, "type"),
				RedenJson.string(object, "name"),
				RedenJson.string(object, "key"),
				RedenJson.bool(object, "hasX"),
				RedenJson.bool(object, "hasY"),
				RedenJson.bool(object, "hasZ"),
				readConditions(object),
				RedenJson.string(object, "link"),
				RedenJson.string(object, "summary"),
				RedenJson.string(object, "description"),
				RedenJson.longValue(object, "updatedAt"),
				RedenAuthor.fromJson(RedenJson.object(object, "author")),
				RedenJson.string(object, "imageUrl"),
				RedenJson.string(object, "thumbnailUrl"),
				readAttachments(object),
				RedenJson.integer(object, "downloads"),
				RedenJson.stringList(object, "images"),
				readTags(object, "featureTags"),
				RedenJson.stringList(object, "versions"),
				RedenJson.bool(object, "original"),
				RedenJson.integer(object, "upVotes"),
				RedenJson.integer(object, "downVotes"),
				RedenJson.string(object, "language")
		);
	}

	private static Map<String, List<String>> readConditions(JsonObject object) {
		JsonObject conditionsObject = RedenJson.object(object, "conditions");
		Map<String, List<String>> conditions = new LinkedHashMap<>();

		for (String axis : List.of("x", "y", "z")) {
			List<String> axisConditions = RedenJson.stringList(conditionsObject, axis);

			if (!axisConditions.isEmpty()) {
				conditions.put(axis, axisConditions);
			}
		}

		return Map.copyOf(conditions);
	}

	private static List<RedenAttachment> readAttachments(JsonObject object) {
		List<RedenAttachment> attachments = new ArrayList<>();

		for (JsonElement element : RedenJson.array(object, "attachments")) {
			if (element.isJsonObject()) {
				attachments.add(RedenAttachment.fromJson(element.getAsJsonObject()));
			}
		}

		return List.copyOf(attachments);
	}

	private static List<RedenTag> readTags(JsonObject object, String key) {
		List<RedenTag> tags = new ArrayList<>();

		for (JsonElement element : RedenJson.array(object, key)) {
			if (element.isJsonObject()) {
				tags.add(RedenTag.fromJson(element.getAsJsonObject()));
			}
		}

		return List.copyOf(tags);
	}
}
