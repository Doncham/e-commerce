package kr.hhplus.be.server.application.product.batch;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.application.product.ProductSnap;
import kr.hhplus.be.server.application.product.ProductSoldQtyDTO;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.redis.PopularRankRedisWriter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PopularRankRebuildService {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	// daily 키는 7/30 롤링용으로 넉넉히 40일 보관
	private static final Duration WINDOW_TTL = Duration.ofDays(36);
	private static final Duration SNAP_TTL = Duration.ofHours(24);
	private final Clock clock;
	private final OrderProductRepository orderProductRepository;
	private final ProductRepository productRepository;
	private final PopularRankRedisWriter redisWriter;

	@Transactional(readOnly = true)
	public void rebuild7d(int topN) {
		LocalDate today = LocalDate.now(clock.withZone(KST));
		LocalDateTime to = today.atStartOfDay();
		LocalDateTime from = to.minusDays(7);

		List<ProductSoldQtyDTO> rows = orderProductRepository.findPopularProduct(
			from, to, OrderStatus.PAID, PageRequest.of(0, topN)
		);

		// 1) 랭킹 반영(ZSET)
		redisWriter.rebuildZsetWithSwap("rank:7d", rows, WINDOW_TTL);

		// 2) topN 스냅샷 프리워밍
		warmProductSnapshots(rows, SNAP_TTL);
	}

	@Transactional(readOnly = true)
	public void rebuild30d(int topN) {
		LocalDate today = LocalDate.now(clock.withZone(KST));
		LocalDateTime to = today.atStartOfDay();
		LocalDateTime from = to.minusDays(30);

		List<ProductSoldQtyDTO> rows = orderProductRepository.findPopularProduct(
			from, to, OrderStatus.PAID, PageRequest.of(0, topN)
		);

		redisWriter.rebuildZsetWithSwap("rank:30d", rows, WINDOW_TTL);

		warmProductSnapshots(rows, SNAP_TTL);
	}

	private void warmProductSnapshots(List<ProductSoldQtyDTO> rows, Duration ttl) {
		if (rows == null || rows.isEmpty()) return;
		List<Long> productIds = rows.stream()
			.map(ProductSoldQtyDTO::getProductId)
			.filter(id -> id != null && id > 0)
			.distinct()
			.toList();

		if(productIds.isEmpty()) return;

		List<Product> products = productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(
			productIds);

		List<ProductSnap> snaps = products.stream()
			.map(ProductSnap::from)
			.toList();

		redisWriter.writeProductSnapshots(snaps, ttl);
	}
}
