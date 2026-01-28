package kr.hhplus.be.server.application.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.api.product.response.PopularProductsResponse;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;

@ExtendWith(MockitoExtension.class)
class PopularProductReadTest {
	@Mock
	InventoryRepository inventoryRepository; // getPopulars에는 사용 안 함
	@Mock
	OrderProductRepository orderProductRepository;
	@Mock
	ProductRepository productRepository;

	@InjectMocks
	ProductService productService;

	@Test
	void getPopulars_emptyAggResult_returnsEmptyItems() {
		// given
		given(orderProductRepository.findPopularProduct(any(), any(), eq(OrderStatus.PAID), any(Pageable.class)))
			.willReturn(List.of());

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res).isNotNull();
		assertThat(res.getItems()).isEmpty();

	}

	@Test
	void getPopulars_mapsProductsAndRanks_andSkipsMissingProduct() {
		// given: 집계 결과(정렬된 순서라고 가정)
		// 예: 101이 1등, 202가 2등, 303이 3등
		List<ProductSoldQtyDTO> agg = List.of(
			new ProductSoldQtyDTO(101L, 50L),
			new ProductSoldQtyDTO(202L, 40L),
			new ProductSoldQtyDTO(303L, 30L)
		);

		given(orderProductRepository.findPopularProduct(any(), any(), eq(OrderStatus.PAID), any(Pageable.class)))
			.willReturn(agg);

		// productRepository가 202는 못 찾았다고 가정(비활성화/삭제/TOCTOU 등)
		Product p101 = product(101L, "A", 1000L);
		Product p303 = product(303L, "C", 3000L);

		given(productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(List.of(101L, 202L, 303L)))
			.willReturn(List.of(p101, p303));

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res.getItems()).hasSize(2);

		// rank는 “응답에 담긴 순서 기준”으로 1,2가 된다(중간 스킵했으니까)
		assertThat(res.getItems().get(0).getRank()).isEqualTo(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(101L);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(50L);

		assertThat(res.getItems().get(1).getRank()).isEqualTo(2);
		assertThat(res.getItems().get(1).getProductId()).isEqualTo(303L);
		assertThat(res.getItems().get(1).getSoldQty()).isEqualTo(30L);

		// 호출 파라미터(기본 검증)
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		then(orderProductRepository).should()
			.findPopularProduct(any(LocalDateTime.class), any(LocalDateTime.class), eq(OrderStatus.PAID), pageableCaptor.capture());

		assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20); // 서비스 상수 POPULAR_PRODUCT_LIMIT
	}

	private Product product(Long id, String name, Long price) {
		// 여기서는 "개념 코드"로만 둔다.
		Product p = Product.createProduct(name, "desc", price);
		// id 세팅이 필요하면:
		ReflectionTestUtils.setField(p, "id", id);
		return p;
	}
}