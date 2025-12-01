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
@Builder(builderMethodName = "paymentChannelWith")
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payment_channel")
public class PaymentChannel {

    public final static PaymentChannel TELEPHONY = new PaymentChannel("telephony", "Through the IVR");
    public final static PaymentChannel ONLINE = new PaymentChannel("online", "Through online portal");

    @Id
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;
}

