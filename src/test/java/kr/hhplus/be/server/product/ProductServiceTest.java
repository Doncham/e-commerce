package kr.hhplus.be.server.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.hhplus.be.server.inventory.Inventory;
import kr.hhplus.be.server.inventory.InventoryRepository;
import kr.hhplus.be.server.inventory.InventoryStatus;
import kr.hhplus.be.server.inventory.exception.NotFoundInventoryException;
import kr.hhplus.be.server.product.response.ProductDetailResponse;
import kr.hhplus.be.server.user.User;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
	@InjectMocks
	private ProductService productService;

	@Mock
	private InventoryRepository inventoryRepository;

	@Mock
	private User user;

	// 상품 조회 성공 단위 테스트
	@Test
	void givenValidProductId_whenGetProductDetail_thenReturnProduct() {
		// given
		long productId = 1L;
		long inventoryId = 1L;

		when(inventoryRepository.findByProductId(productId)).thenReturn(
			Optional.of(Inventory.builder()
				.stock(100L)
				.reserved(95L)
				.product(
					Product.builder().name("Test Product").price(1000L).build()
				)
				.build())
		);
		// when
		ProductDetailResponse dto = productService.getProductDetail(productId);

		// then
		assertEquals("Test Product", dto.getName());
		assertEquals(1000L, dto.getPrice());
		assertEquals(InventoryStatus.LOW_STOCK.toString(), dto.getInventoryStatus());
		assertEquals(5L, dto.getStock());
	}

	@Test
	void givenInvalidProductId_whenGetProductDetail_thenThrowNotFoundInventoryException() {
		// given
		long productId = 999L;

		when(inventoryRepository.findByProductId(productId)).thenReturn(
			Optional.empty()
		);
		// when & then
		assertThrows(NotFoundInventoryException.class, () -> {
			productService.getProductDetail(productId);
		});
	}

	@Test
	void givenZeroStock_whenGetProductDetail_thenReturnSoldOutStatus() {
		// given
		long productId = 2L;

		when(inventoryRepository.findByProductId(productId)).thenReturn(
			Optional.of(Inventory.builder()
				.stock(50L)
				.reserved(50L)
				.product(
					Product.builder().name("Sold Out Product").price(2000L).build()
				)
				.build())
		);
		// when
		ProductDetailResponse dto = productService.getProductDetail(productId);

		// then
		assertEquals("Sold Out Product", dto.getName());
		assertEquals(2000L, dto.getPrice());
		assertEquals(InventoryStatus.OUT_OF_STOCK.toString(), dto.getInventoryStatus());
		assertEquals(0L, dto.getStock());
	}

	@Test
	void givenHighAvailableStock_whenGetProductDetail_thenReturnInStockStatus() {
		// given
		long productId = 3L;

		when(inventoryRepository.findByProductId(productId)).thenReturn(
			Optional.of(Inventory.builder()
				.stock(100L)
				.reserved(20L)
				.product(
					Product.builder().name("In Stock Product").price(3000L).build()
				)
				.build())
		);
		// when
		ProductDetailResponse dto = productService.getProductDetail(productId);

		// then
		assertEquals("In Stock Product", dto.getName());
		assertEquals(3000L, dto.getPrice());
		assertEquals(InventoryStatus.IN_STOCK.toString(), dto.getInventoryStatus());
		assertEquals(null, dto.getStock());
	}
}