package kg.gov.nas.licensedb.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "freq") // Точное имя таблицы
public class FreqEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID") // Важно: в базе это ID (большими буквами)
    private Long id;

    @Column(name = "IDowner") // ID владельца (число)
    private Integer idOwner;

    @Column(name = "nominal") // Частота
    private Double nominal;

    @Column(name = "signature") // Наше поле защиты
    private String signature;
    
    // Остальные поля (IDsite, band и т.д.) MySQL заполнит нулями сам
}