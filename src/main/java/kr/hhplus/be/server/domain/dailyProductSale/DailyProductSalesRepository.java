package kr.hhplus.be.server.domain.dailyProductSale;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.application.product.PopularProductRow;

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

	@Query(value = """
  	select 
  		p.id as productId,
  		p.name as productName,
  		p.price as price,
  		p.sold_qty as soldQty,
  		popular.total_count as totalCount
  	from (
  		select 
  			dps.product_id, 
  			sum(dps.paid_count) as total_count,
		from daily_product_sales as dps
		where dps.sales_date >= :startDate 
			and sales_date <= :endDate 
		group by dps.product_id
		order by total_count desc, dps.product_id asc
		limit :topN
	) popular
	join product p on popular.product_id = p.id
""", nativeQuery = true)
	List<PopularProductRow> findTopProductByPaidCount(
		@Param("startDate") LocalDate startDate,
		@Param("endDate") LocalDate endDate,
		@Param("topN") Integer topN
	);
}
