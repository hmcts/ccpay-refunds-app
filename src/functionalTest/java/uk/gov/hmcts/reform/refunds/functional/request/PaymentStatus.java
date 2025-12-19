package uk.gov.hmcts.reform.refunds.functional.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder(builderMethodName = "paymentStatusWith")
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payment_status")
public class PaymentStatus {

    public static final PaymentStatus CREATED = new PaymentStatus("created", "created");
    public static final PaymentStatus SUCCESS = new PaymentStatus("success", "success");
    public static final PaymentStatus CANCELLED = new PaymentStatus("cancelled", "cancelled");
    public static final PaymentStatus PENDING = new PaymentStatus("pending", "pending");
    public static final PaymentStatus ERROR = new PaymentStatus("error", "error");
    public static final PaymentStatus FAILED = new PaymentStatus("failed", "failed");

    @Id
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;
}

