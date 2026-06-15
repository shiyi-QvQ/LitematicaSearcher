package com.litematicasearcher.client.api;

public final class RedenApiException extends RuntimeException {
	public RedenApiException(String message) {
		super(message);
	}

	public RedenApiException(String message, Throwable cause) {
		super(message, cause);
	}
}
