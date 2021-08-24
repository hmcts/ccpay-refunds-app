package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryDto;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;

@Component
public class StatusHistoryResponseMapper {

    public StatusHistoryDto getStatusHistoryDto(StatusHistory statusHistory) {
        return StatusHistoryDto
                .buildStatusHistoryDtoWith()
                .id(statusHistory.getId())
                .refundsId(statusHistory.getRefund().getId())
                .status(statusHistory.getStatus())
                .notes(statusHistory.getNotes())
                .dateCreated(statusHistory.getDateCreated().toString())
                .createdBy(statusHistory.getCreatedBy())
                .build();

    }
}
