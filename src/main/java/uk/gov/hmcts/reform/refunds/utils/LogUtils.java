package uk.gov.hmcts.reform.refunds.utils;

public final class LogUtils {

    private LogUtils() {
    }

    /**
     * Removes CR/LF/TAB characters to prevent log forging from user-controlled input.
     */
    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}
