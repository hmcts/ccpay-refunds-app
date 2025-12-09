package uk.gov.hmcts.reform.refunds.utils;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class DateUtilTest {

    @Test
    void dateToLocalDateTime_shouldReturnNull_whenDateIsNull() {
        assertNull(DateUtil.dateToLocalDateTime(null));
    }

    @Test
    void dateToLocalDateTime_shouldReturnLocalDateTime_whenDateIsNotNull() {
        Date now = new Date();
        LocalDateTime ldt = DateUtil.dateToLocalDateTime(now);
        assertNotNull(ldt);
        assertEquals(now.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(), ldt);
    }

    @Test
    void atStartOfDay_shouldReturnNull_whenDateIsNull() {
        assertNull(DateUtil.atStartOfDay(null));
    }

    @Test
    void atStartOfDay_shouldReturnStartOfDay_whenDateIsNotNull() {
        Date now = new Date();
        Date startOfDay = DateUtil.atStartOfDay(now);
        LocalDateTime ldt = DateUtil.dateToLocalDateTime(now);
        LocalDateTime expected = ldt.with(LocalTime.MIN);
        assertEquals(expected, DateUtil.dateToLocalDateTime(startOfDay));
    }

    @Test
    void atEndOfDay_shouldReturnNull_whenDateIsNull() {
        assertNull(DateUtil.atEndOfDay(null));
    }

    @Test
    void atEndOfDay_shouldReturnEndOfDay_whenDateIsNotNull() {
        Date now = new Date();
        Date endOfDay = DateUtil.atEndOfDay(now);
        LocalDateTime ldt = DateUtil.dateToLocalDateTime(now);
        LocalDateTime expected = ldt.with(LocalTime.MAX).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        LocalDateTime actual = DateUtil.dateToLocalDateTime(endOfDay).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        assertEquals(expected, actual);
    }

    @Test
    void localDateTimeToDate_shouldReturnNull_whenLocalDateTimeIsNull() {
        assertNull(DateUtil.localDateTimeToDate(null));
    }

    @Test
    void localDateTimeToDate_shouldReturnDate_whenLocalDateTimeIsNotNull() {
        LocalDateTime ldt = LocalDateTime.now().withNano((LocalDateTime.now().getNano() / 1_000_000) * 1_000_000);
        Date date = DateUtil.localDateTimeToDate(ldt);
        assertNotNull(date);
        LocalDateTime actual = DateUtil.dateToLocalDateTime(date);
        // Truncate both to milliseconds for comparison
        assertEquals(ldt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS), actual.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    }

    @Test
    void getIsoDateTimeFormatter_shouldReturnFormatterThatParsesSupportedDate() {
        DateUtil dateUtil = new DateUtil();
        DateTimeFormatter formatter = dateUtil.getIsoDateTimeFormatter();
        assertNotNull(formatter);

        // Test parsing a supported date format
        String dateStr = "2024-06-01 12:34:56";
        DateTime dateTime = formatter.parseDateTime(dateStr);
        assertEquals(2024, dateTime.getYear());
        assertEquals(6, dateTime.getMonthOfYear());
        assertEquals(1, dateTime.getDayOfMonth());
        assertEquals(12, dateTime.getHourOfDay());
        assertEquals(34, dateTime.getMinuteOfHour());
        assertEquals(56, dateTime.getSecondOfMinute());
    }
}
