package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;

@Component
public class StatusHistoryResponseMapper {

    public StatusHistoryDto getStatusHistoryDto(StatusHistory statusHistory, UserIdentityDataDto userData) {
        return StatusHistoryDto
                .buildStatusHistoryDtoWith()
                .id(statusHistory.getId())
                .refundsId(statusHistory.getRefund().getId())
                .status(statusHistory.getStatus())
                .notes(statusHistory.getNotes())
                .dateCreated(statusHistory.getDateCreated())
                .createdBy(userData.getFullName())
                .build();

    }
}
