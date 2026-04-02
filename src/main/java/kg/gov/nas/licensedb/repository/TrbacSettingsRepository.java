package kg.gov.nas.licensedb.repository;

import kg.gov.nas.licensedb.entity.TrbacSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrbacSettingsRepository extends JpaRepository<TrbacSettingsEntity, Long> {
}