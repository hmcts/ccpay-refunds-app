package uk.gov.hmcts.reform.refunds.model;

import lombok.*;

import javax.persistence.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "reasons")
public class Reason {

    public final static Reason REASON1 = new Reason("reason1", "reason1");
    public final static Map<String,Reason> reasonMap;
    static {
        reasonMap = new HashMap<>();
        reasonMap.put("reason1",REASON1);
    }


    @Id
    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    public static Reason getReasonObject(String reason){
        return reasonMap.get(reason);
    }

}
