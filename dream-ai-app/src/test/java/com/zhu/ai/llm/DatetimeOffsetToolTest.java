package com.zhu.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DatetimeOffsetToolTest {

    @Test
    void shiftsLocalDateTimeByDaysInShanghai() {
        DatetimeOffsetTool.Result result = DatetimeOffsetTool.execute(
                new DatetimeOffsetTool.Request("2026-09-10 10:00:00", "Asia/Shanghai", 3L, "DAYS"));

        assertEquals("Asia/Shanghai", result.zoneId());
        assertEquals("2026-09-10 10:00:00", result.baseDateTime());
        assertEquals("2026-09-13 10:00:00", result.resultDateTime());
        assertEquals("星期日", result.dayOfWeek());
        assertEquals(3, result.amount());
        assertEquals("DAYS", result.unit());
    }

    @Test
    void shiftsByHoursAcrossMidnight() {
        DatetimeOffsetTool.Result result = DatetimeOffsetTool.execute(
                new DatetimeOffsetTool.Request("2026-09-10 22:30:00", null, 3L, "HOURS"));

        assertEquals("Asia/Shanghai", result.zoneId());
        assertEquals("2026-09-11 01:30:00", result.resultDateTime());
        assertEquals("星期五", result.dayOfWeek());
    }

    @Test
    void acceptsIsoLocalDateTimeAndMinutes() {
        DatetimeOffsetTool.Result result = DatetimeOffsetTool.execute(
                new DatetimeOffsetTool.Request("2026-09-10T10:00:00", "UTC", -90L, "MINUTES"));

        assertEquals("UTC", result.zoneId());
        assertEquals("2026-09-10 08:30:00", result.resultDateTime());
    }

    @Test
    void rejectsBlankUnit() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DatetimeOffsetTool.execute(
                        new DatetimeOffsetTool.Request("2026-09-10 10:00:00", null, 1L, " ")));
        assertTrue(ex.getMessage().contains("unit"));
    }

    @Test
    void rejectsUnknownUnit() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DatetimeOffsetTool.execute(
                        new DatetimeOffsetTool.Request("2026-09-10 10:00:00", null, 1L, "WEEKS")));
        assertTrue(ex.getMessage().contains("unit"));
    }

    @Test
    void rejectsBadZone() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DatetimeOffsetTool.execute(
                        new DatetimeOffsetTool.Request("2026-09-10 10:00:00", "Mars/Phobos", 1L, "DAYS")));
        assertTrue(ex.getMessage().contains("zoneId"));
    }

    @Test
    void rejectsUnparseableBase() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> DatetimeOffsetTool.execute(
                        new DatetimeOffsetTool.Request("not-a-date", null, 1L, "DAYS")));
        assertTrue(ex.getMessage().contains("baseDateTime"));
    }
}
