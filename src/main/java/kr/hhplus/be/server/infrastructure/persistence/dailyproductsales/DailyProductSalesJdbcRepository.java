package kr.hhplus.be.server.infrastructure.persistence.dailyproductsales;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.application.product.batch.DailySalesRebuildRow;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DailyProductSalesJdbcRepository {
	private final JdbcTemplate jdbcTemplate;

	public int[][] batchInsert(List<DailySalesRebuildRow> rows) {
		String sql = """
            insert into daily_product_sales (
                sales_date,
                product_id,
                paid_count
            ) values (?, ?, ?)
            """;

		return jdbcTemplate.batchUpdate(
			sql,
			rows,
			1000,
			(ps, row) -> {
				ps.setObject(1, row.getDate());
				ps.setLong(2, row.getProductId());
				ps.setLong(3, row.getSalesCount());
			}
		);
	}
}
