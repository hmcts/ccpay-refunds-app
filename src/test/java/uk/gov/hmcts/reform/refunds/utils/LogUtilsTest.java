package uk.gov.hmcts.reform.refunds.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogUtilsTest {

    @Test
    void sanitize_shouldReturnNullWhenInputIsNull() {
        assertNull(LogUtils.sanitize(null));
    }

    @Test
    void sanitize_shouldReplaceControlCharacters() {
        assertEquals("17477_38470_383168", LogUtils.sanitize("17477\n38470\r383168"));
        assertEquals("RF-1234_5678", LogUtils.sanitize("RF-1234\t5678"));
    }

    @Test
    void sanitize_shouldLeaveValidValuesUnchanged() {
        assertEquals("1747738470383168", LogUtils.sanitize("1747738470383168"));
    }
}
