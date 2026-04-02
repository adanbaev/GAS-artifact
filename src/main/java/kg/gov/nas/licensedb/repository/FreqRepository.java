package kg.gov.nas.licensedb.repository;

import kg.gov.nas.licensedb.entity.FreqEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FreqRepository extends JpaRepository<FreqEntity, Long> {
}