package kg.gov.nas.licensedb.entity;

import kg.gov.nas.licensedb.enums.OwnerBasis;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
//@Entity
//@Table(name = "owner_detail")
public class OwnerDetail {

    @Id
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "inn")
    private String inn;

    @Column(name = "basis")
    @Enumerated(EnumType.STRING)
    private OwnerBasis basis;
}