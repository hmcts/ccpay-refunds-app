package uk.gov.hmcts.reform.refunds.utils;

public final class LogUtils {

    private LogUtils() {
        // utility class
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}
