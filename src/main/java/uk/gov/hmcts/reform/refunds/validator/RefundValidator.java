package uk.gov.hmcts.reform.refunds.validator;

import org.joda.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.utils.DateUtil;

import java.time.format.DateTimeParseException;
import java.util.Optional;

import static java.util.Optional.empty;

@Component
public class RefundValidator {

    @Autowired
    private DateUtil dateUtil;

    public void validate(Optional<String> startDateString, Optional<String> endDateString) {

        Optional<LocalDateTime> startDate = parseAndValidateDate(startDateString);
        Optional<LocalDateTime> endDate = parseAndValidateDate(endDateString);

        if (startDate.isPresent() && endDate.isPresent() && startDate.get().isAfter(endDate.get())) {

            throw new InvalidRefundRequestException("Start date cannot be greater than end date");

        }

    }

    private Optional<LocalDateTime> parseAndValidateDate(Optional<String> dateTimeString) {
        return dateTimeString.flatMap(s -> validateDate(s));
    }

    private Optional<LocalDateTime> validateDate(String dateString) {
        Optional<LocalDateTime> formattedDate = parseFrom(dateString);
        if (formattedDate.isPresent()) {
            checkFutureDate(formattedDate.get());
        } else {
            throw new InvalidRefundRequestException("Invalid date format received, required data format is ISO");
        }
        return formattedDate;
    }

    private void checkFutureDate(LocalDateTime date) {
        if (date.isAfter(LocalDateTime.now())) {
            throw new InvalidRefundRequestException("Date cannot be in the future");
        }
    }

    private Optional<LocalDateTime> parseFrom(String value) {
        try {
            return Optional.of(LocalDateTime.parse(value, dateUtil.getIsoDateTimeFormatter()));
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            return empty();
        }
    }
}
