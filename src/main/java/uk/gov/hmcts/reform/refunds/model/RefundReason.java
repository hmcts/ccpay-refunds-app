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
public class RefundReason {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

}
