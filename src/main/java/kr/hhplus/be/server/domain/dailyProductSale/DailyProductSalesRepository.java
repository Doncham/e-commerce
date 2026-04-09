package kr.hhplus.be.server.domain.dailyProductSale;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyProductSalesRepository extends JpaRepository<DailyProductSales, DailyProductSalesId> {
	@Modifying
	@Query(value = """
		insert into daily_product_sales (
			sales_date,
			product_id,
			paid_count
		) values (
			:salesDate,
			:productId,
			1
		)
		on duplicate key update
			paid_count = paid_count + 1
""", nativeQuery = true)
	int increasePaidCount(
		@Param("salesDate") LocalDate salesDate,
		@Param("productId") Long productId
	);
}
