package kg.gov.nas.licensedb.repository;

import kg.gov.nas.licensedb.entity.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    List<UserFavorite> findTop10ByUser_IdOrderByCreatedAtMsDesc(Long userId);

    Optional<UserFavorite> findByIdAndUser_Id(Long id, Long userId);

    List<UserFavorite> findByUser_IdOrderByCreatedAtMsDesc(Long userId);
}