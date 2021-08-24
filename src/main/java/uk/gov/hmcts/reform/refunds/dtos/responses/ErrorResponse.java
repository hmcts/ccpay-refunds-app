package uk.gov.hmcts.reform.refunds.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@Setter
@Getter
@NoArgsConstructor
public class ErrorResponse {
    private String message;
    private List<String> details;
}
