package uk.gov.hmcts.reform.refunds.dtos.responses;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(builderMethodName = "notificationWith")
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Notification {

    private String reference;

    private String notificationType;

    private ContactDetailsDto contactDetails;

    private Date dateCreated;

    private Date dateUpdated;
}
