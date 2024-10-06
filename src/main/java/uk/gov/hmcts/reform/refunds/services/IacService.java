package uk.gov.hmcts.reform.refunds.services;

import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.refunds.dtos.SupplementaryDetailsResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberata;

import java.util.List;

public interface IacService {
    ResponseEntity<SupplementaryDetailsResponse> getIacSupplementaryDetails(List<RefundLiberata> refundsDtos, String serviceName);

    List<RefundLiberata> updateIacSupplementaryDetails(List<RefundLiberata> refundDtos, SupplementaryDetailsResponse supplementaryDetailsResponse);
}
