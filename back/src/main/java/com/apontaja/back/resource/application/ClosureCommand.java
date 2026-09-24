package com.apontaja.back.resource.application;

/** Dates ISO 8601 avec offset (ex. 2026-08-15T00:00:00+02:00). */
public record ClosureCommand(String startAt, String endAt, String reason) {
}
