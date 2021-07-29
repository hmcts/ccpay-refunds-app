package uk.gov.hmcts.reform.refunds.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import javax.validation.constraints.NotNull;
import java.util.Date;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class PaymentAllocationDto {


    private String id;

    private String paymentReference;

    private String paymentGroupReference;

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
