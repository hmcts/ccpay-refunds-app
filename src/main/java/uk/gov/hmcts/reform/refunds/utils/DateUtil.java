package uk.gov.hmcts.reform.refunds.utils;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.DateTimeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

@Component
public class DateUtil {

    private static final Logger LOG = LoggerFactory.getLogger(DateUtil.class);

    private static final DateTimeParser[] ISO_DATE_TIME_PARSERS = {
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").getParser(),
        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").getParser(),
        DateTimeFormat.forPattern("dd-MM-yyyy HH:mm:ss").getParser(),
        DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss").getParser(),
        DateTimeFormat.forPattern("dd-MM-yyyy'T'HH:mm:ss").getParser(),
        DateTimeFormat.forPattern("yyyy-MM-dd").getParser(),
        DateTimeFormat.forPattern("dd-MM-yyyy").getParser(),
    };

    public DateTimeFormatter getIsoDateTimeFormatter() {
        return new DateTimeFormatterBuilder().append(null, ISO_DATE_TIME_PARSERS).toFormatter();
    }

    public static LocalDateTime dateToLocalDateTime(Date date) {
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    public static Date atStartOfDay(Date date) {
        LocalDateTime localDateTime = dateToLocalDateTime(date);
        LocalDateTime startOfDay = localDateTime == null ? null : localDateTime.with(LocalTime.MIN);
        LOG.error("Start date {} ", date);
        return localDateTimeToDate(startOfDay);
    }

    public static Date atEndOfDay(Date date) {
        LocalDateTime localDateTime = dateToLocalDateTime(date);
        LocalDateTime endOfDay = localDateTime == null ? null : localDateTime.with(LocalTime.MAX);
        LOG.error("End date {} ", date);
        return localDateTimeToDate(endOfDay);
    }

    public static Date localDateTimeToDate(LocalDateTime ldt) {
        return ldt == null ? null : Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }


    public static String toDdMmYyyy(String dateStr) {
        if (dateStr == null) {
            return null;
        }
        // Handle YYYY-MM-DD
        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            String[] parts = dateStr.split("-");
            return parts[2] + "/" + parts[1] + "/" + parts[0];
        }
        // Handle MM/DD/YYYY
        if (dateStr.matches("\\d{2}/\\d{2}/\\d{4}")) {
            String[] parts = dateStr.split("/");
            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);
            // If month <= 12 and day > 12, it's MM/DD/YYYY
            if (month <= 12 && day > 12) {
                return parts[1] + "/" + parts[0] + "/" + parts[2];
            }
            // If day <= 12, ambiguous: default to MM/DD/YYYY
            if (month <= 12 && day <= 12) {
                return parts[1] + "/" + parts[0] + "/" + parts[2];
            }
            // If month > 12, treat as DD/MM/YYYY
            return dateStr;
        }
        // Handle DD/MM/YYYY
        if (dateStr.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return dateStr;
        }
        throw new IllegalArgumentException("Invalid date format. Use dd/MM/yyyy, MM/dd/yyyy, or yyyy-MM-dd.");
    }

}
