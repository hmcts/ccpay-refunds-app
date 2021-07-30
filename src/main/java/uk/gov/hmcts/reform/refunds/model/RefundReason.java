package uk.gov.hmcts.reform.refunds.model;

import lombok.*;

import javax.persistence.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "refund_reasons")
@Builder(builderMethodName = "refundsReasonWith")
public class RefundReason {

    public final static RefundReason REASON1 = new RefundReason("RESN1", "reason1", "reason1");
    public final static Map<String,RefundReason> reasonMap;
    static {
        reasonMap = new HashMap<>();
        reasonMap.put("RESN1",REASON1);
    }


    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    public static Optional<RefundReason> getReasonObject(String reason){
        return Optional.ofNullable(reasonMap.get(reason));
    }

}
