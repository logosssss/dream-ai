package com.zhu.ai.llm;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

/**
 * 进程内业务工具逻辑：按时区对基准时间做加减。
 * schema / Bean 装配在 {@code ChatConfig}；本类可单测，不依赖 Spring。
 */
public final class DatetimeOffsetTool {

    public static final String TOOL_NAME = "datetime_offset";

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter OUT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LOCAL_SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DatetimeOffsetTool() {}

    /**
     * @param baseDateTime 基准本地时间；空则取「该时区当前时刻」。支持 {@code yyyy-MM-dd HH:mm:ss} 或 ISO_LOCAL_DATE_TIME
     * @param zoneId IANA 时区；空则 {@code Asia/Shanghai}
     * @param amount 可正可负
     * @param unit {@code DAYS} / {@code HOURS} / {@code MINUTES}（大小写不敏感）
     */
    public record Request(String baseDateTime, String zoneId, Long amount, String unit) {}

    public record Result(
            String zoneId,
            String baseDateTime,
            String resultDateTime,
            String dayOfWeek,
            long amount,
            String unit) {}

    public static Result execute(Request request) {
        Objects.requireNonNull(request, "request");
        if (request.amount() == null) {
            throw new IllegalArgumentException("amount required");
        }
        ZoneId zone = resolveZone(request.zoneId());
        String unit = normalizeUnit(request.unit());
        ZonedDateTime base = resolveBase(request.baseDateTime(), zone);
        ZonedDateTime shifted = shift(base, request.amount(), unit);
        return new Result(
                zone.getId(),
                base.format(OUT_FMT),
                shifted.format(OUT_FMT),
                dayOfWeekZh(shifted.getDayOfWeek()),
                request.amount(),
                unit);
    }

    private static ZoneId resolveZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(zoneId.trim());
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("invalid zoneId: " + zoneId.trim(), ex);
        }
    }

    private static String normalizeUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit required: DAYS|HOURS|MINUTES");
        }
        String normalized = unit.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DAYS", "HOURS", "MINUTES" -> normalized;
            default -> throw new IllegalArgumentException("unit required: DAYS|HOURS|MINUTES");
        };
    }

    private static ZonedDateTime resolveBase(String baseDateTime, ZoneId zone) {
        if (baseDateTime == null || baseDateTime.isBlank()) {
            return ZonedDateTime.now(zone);
        }
        String raw = baseDateTime.trim();
        try {
            return LocalDateTime.parse(raw, LOCAL_SPACE).atZone(zone);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "invalid baseDateTime (use yyyy-MM-dd HH:mm:ss or ISO_LOCAL_DATE_TIME): " + raw, ex);
        }
    }

    private static ZonedDateTime shift(ZonedDateTime base, long amount, String unit) {
        return switch (unit) {
            case "DAYS" -> base.plusDays(amount);
            case "HOURS" -> base.plusHours(amount);
            case "MINUTES" -> base.plusMinutes(amount);
            default -> throw new IllegalArgumentException("unit required: DAYS|HOURS|MINUTES");
        };
    }

    private static String dayOfWeekZh(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.CHINA);
    }
}
