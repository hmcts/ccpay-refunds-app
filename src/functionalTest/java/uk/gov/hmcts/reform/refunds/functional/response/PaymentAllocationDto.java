package uk.gov.hmcts.reform.refunds.functional.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@Builder(builderMethodName = "paymentAllocationDtoWith")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentAllocationDto {


    private String id;

    @NotNull
    private String paymentReference;

    @NotNull
    private String paymentGroupReference;

    @NotNull
    private PaymentAllocationStatus paymentAllocationStatus;

    private String unidentifiedReason;

    private String receivingOffice;

    private String reason;

    private String explanation;

    private String userId;

    private String userName;

    // This field added due to Libereta Changes. This is just a duplication of paymentAllocationStatus and unidentifiedReason parameters.
    private String allocationStatus;

    private String allocationReason;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "GMT")
    private Date dateCreated;

}
