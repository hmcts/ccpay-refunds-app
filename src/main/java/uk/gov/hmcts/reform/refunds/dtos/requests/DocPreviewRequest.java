package uk.gov.hmcts.reform.refunds.dtos.requests;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "docPreviewRequestWith")
public class DocPreviewRequest {

    private String paymentReference;

    @NotNull(message = "Payment method cannot be null")
    @NotEmpty(message = "Payment method cannot be blank")
    private String paymentMethod;
    @NotNull(message = "Payment channel cannot be null")
    @NotEmpty(message = "Payment channel cannot be blank")
    private String paymentChannel;
    @NotNull(message = "Service cannot be null")
    @NotEmpty(message = "Service cannot be blank")
    private String serviceName;

    @Valid
    private RecipientPostalAddress recipientPostalAddress;
    private String recipientEmailAddress;

    @NotNull
    private NotificationType notificationType;

    @NotNull
    @Valid
    private Personalisation personalisation;
}
