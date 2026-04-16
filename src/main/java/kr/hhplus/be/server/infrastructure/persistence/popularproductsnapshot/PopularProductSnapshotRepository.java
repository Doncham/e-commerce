package kr.hhplus.be.server.infrastructure.persistence.popularproductsnapshot;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.application.product.PopularDateRange;
import kr.hhplus.be.server.domain.popular_product_snapshot.PopularProductSnapshot;
@Repository
public interface PopularProductSnapshotRepository extends JpaRepository<PopularProductSnapshot, Long> {
	@Modifying
	@Query(value = """
		insert into popular_product_snapshot (
			range_type,
			json,
			created_at
		)
		values (
			:popularDateRange,
			:json,
			:createdAt
		)
		on duplicate key update
		json = :json
		created_at = :createdAt

""", nativeQuery = true)
	void upsert(@Param("popularDateRange") String range, @Param("json") String cacheResult, @Param("createdAt") LocalDateTime createdAt);

	Optional<PopularProductSnapshot> findByRangeType(PopularDateRange rangeType);
}
