package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.SupplementaryDetails;
import uk.gov.hmcts.reform.refunds.dtos.SupplementaryDetailsResponse;
import uk.gov.hmcts.reform.refunds.dtos.SupplementaryInfo;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentRefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberata;
import uk.gov.hmcts.reform.refunds.model.IacSupplementaryRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class IacServiceImpl implements IacService {
    private static final Logger LOG = LoggerFactory.getLogger(IacServiceImpl.class);

    @Value("${iac.supplementary.info.url}")
    private String iacSupplementaryInfoUrl;

    @Autowired
    @Qualifier("restTemplateIacSupplementaryInfo")
    private RestTemplate restTemplateIacSupplementaryInfo;

    @Autowired
    private AuthTokenGenerator authTokenGenerator;

    @Override
    public ResponseEntity<SupplementaryDetailsResponse> getIacSupplementaryDetails(List<RefundLiberata> refundsDtos, String serviceName) {
        HttpStatus iacResponseHttpStatus = HttpStatus.OK;
        SupplementaryDetailsResponse supplementaryDetailsResponse = null;

        List<RefundLiberata> iacRefunds = getIacRefunds(serviceName, refundsDtos);
        LOG.info("No of IAC refunds retrieved  : {}", iacRefunds.size());

        List<String> iacCcdCaseNos = iacRefunds.stream().map(refund -> refund.getPayment().getCcdCaseNumber())
            .collect(Collectors.toList());

        if (!iacCcdCaseNos.isEmpty()) {
            LOG.info("List of IAC Ccd Case numbers : {}", iacCcdCaseNos.toString());
            try {
                ResponseEntity<SupplementaryDetailsResponse> responseEntitySupplementaryInfo = getIacSupplementaryInfoResponse(iacCcdCaseNos);
                supplementaryDetailsResponse = responseEntitySupplementaryInfo.getBody();
            } catch (HttpClientErrorException ex) {
                LOG.info("IAC Supplementary information could not be found, exception: {}", ex.getMessage());
                iacResponseHttpStatus = HttpStatus.PARTIAL_CONTENT;
            } catch (Exception ex) {
                LOG.info("Unable to retrieve IAC Supplementary Info information, exception: {}", ex.getMessage());
                iacResponseHttpStatus = HttpStatus.PARTIAL_CONTENT;
            }
        }

        return new ResponseEntity<>(supplementaryDetailsResponse, iacResponseHttpStatus);
    }

    public List<RefundLiberata> updateIacSupplementaryDetails(List<RefundLiberata> refundDtos,
                                                              SupplementaryDetailsResponse supplementaryDetailsResponse) {
        Map<String, SupplementaryDetails> supplementaryDetailsMap = supplementaryDetailsResponse.getSupplementaryInfo().stream()
            .collect(Collectors.toMap(SupplementaryInfo::getCcdCaseNumber, SupplementaryInfo::getSupplementaryDetails));

        for (RefundLiberata refundDto : refundDtos) {
            if (supplementaryDetailsMap.containsKey(refundDto.getPayment().getCcdCaseNumber())) {
                SupplementaryDetails supplementaryDetails = supplementaryDetailsMap.get(refundDto.getPayment().getCcdCaseNumber());
                PaymentRefundDto paymentRefundDto = refundDto.getPayment();
                if (supplementaryDetails.getCaseReferenceNumber() != null) {
                    refundDto.getPayment().setCaseReference(supplementaryDetails.getCaseReferenceNumber());
                    LOG.info("IAC Supplementary info updated for ccdCaseNumber refund : {} with caseReference : {}",
                             paymentRefundDto.getCcdCaseNumber(), paymentRefundDto.getCaseReference());
                }
            }
        }
        return refundDtos;
    }

    private List<RefundLiberata> getIacRefunds(String serviceName, List<RefundLiberata> refundDtos) {
        return refundDtos.stream().filter(refund -> (refund.getPayment().getServiceName().equalsIgnoreCase(serviceName)))
                .collect(Collectors.toList());
    }

    private ResponseEntity<SupplementaryDetailsResponse> getIacSupplementaryInfoResponse(List<String> iacCcdCaseNos) throws RestClientException {

        MultiValueMap<String, String> headerMultiValueMapForIacSuppInfo = new LinkedMultiValueMap<String, String>();
        List<String> serviceAuthTokenPaymentList = new ArrayList<>();

        //Generate token for refund api and replace
        serviceAuthTokenPaymentList.add(authTokenGenerator.generate());

        headerMultiValueMapForIacSuppInfo.put("ServiceAuthorization", serviceAuthTokenPaymentList);
        LOG.info("IAC Supplementary info URL: {}", iacSupplementaryInfoUrl + "/supplementary-details");

        IacSupplementaryRequest iacSupplementaryRequest = IacSupplementaryRequest.createIacSupplementaryRequestWith()
            .ccdCaseNumbers(iacCcdCaseNos).build();

        HttpHeaders headers = new HttpHeaders(headerMultiValueMapForIacSuppInfo);
        final HttpEntity<IacSupplementaryRequest> entity = new HttpEntity<>(iacSupplementaryRequest, headers);
        return this.restTemplateIacSupplementaryInfo.exchange(iacSupplementaryInfoUrl + "/supplementary-details", HttpMethod.POST, entity,
                                                              SupplementaryDetailsResponse.class);
    }
}
