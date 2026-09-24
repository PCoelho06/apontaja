package com.apontaja.back.resource.application;

/** Plage brute reçue de l'API : jour "MONDAY".."SUNDAY", heures "HH:mm". */
public record ScheduleSlotCommand(String dayOfWeek, String startTime, String endTime) {
}
