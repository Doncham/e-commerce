package kr.hhplus.be.server.point;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface PointRepository extends JpaRepository<Point, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT p FROM Point p where p.userId = :userId")
	Optional<Point> findByUserIdForUpdate(Long userId);
}
