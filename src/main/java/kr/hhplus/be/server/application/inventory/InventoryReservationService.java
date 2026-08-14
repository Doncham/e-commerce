package kr.hhplus.be.server.application.inventory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.common.ReservationReleaseReason;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReserveStatus;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.exception.BusinessException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.inventoryReserve.InventoryReserveRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryReservationService {
	private final InventoryReserveRepository invReserveRepo;
	private final InventoryRepository invRepo;

	public void confirm(Order order) {
		List<InventoryReservation> reserves = invReserveRepo
			.findByOrderIdAndStatusForUpdate(order.getId(), InventoryReserveStatus.RESERVED);
		if (reserves.isEmpty()) return; // 멱등/재시도 안전

		List<Long> invIds = reserves.stream().map(InventoryReservation::getInventoryId).sorted().toList();
		List<Inventory> inventories = invRepo.findByIdsForUpdate(invIds);
		Map<Long, Inventory> map = inventories.stream().collect(Collectors.toMap(Inventory::getId, it -> it));

		for (InventoryReservation reservation : reserves) {
			Inventory inv = map.get(reservation.getInventoryId());
			if(reservation.confirm()) {
				inv.confirmReserve(reservation.getQty());
			}
		}
	}

	public void releaseAll(
		long orderId,
		ReservationReleaseReason reason
	) {
		List<InventoryReservation> reserves = invReserveRepo
				.findByOrderIdAndStatusForUpdate(
					orderId,
					InventoryReserveStatus.RESERVED
				);

		if (reserves.isEmpty()) return; // 멱등/재시도 안전

		List<Long> invIds = reserves.stream()
			.map(InventoryReservation::getInventoryId)
			.distinct()
			.sorted()
			.toList();

		List<Inventory> inventories =
			invRepo.findByIdsForUpdate(invIds);

		if (inventories.size() != invIds.size()) {
			throw new IllegalStateException(
				"Some inventories do not exist. "
					+ "orderId=" + orderId
					+ ", requestedIds=" + invIds
					+ ", foundCount=" + inventories.size()
			);
		}

		Map<Long, Inventory> inventoryById =
			inventories.stream()
				.collect(Collectors.toMap(
					Inventory::getId,
					Function.identity()
				));


		// 실무에서는 InventoryReservation의 상태가 RESERVED에서 변경될 때만 실제 재고 차감
		for(InventoryReservation reservation : reserves) {
			Inventory inv = inventoryById.get(reservation.getInventoryId());

			if (inv == null) {
				throw new IllegalStateException(
					"Inventory does not exist. "
						+ "inventoryId="
						+ reservation.getInventoryId()
				);
			}

			if(reservation.release(reason)) {
				inv.releaseReserve(reservation.getQty());
			}
		}
	}

	// 1. 예약 없음 -> 최초 예약
	// 2. 현재 Order와 동일한 RESERVED 예약 존재 -> 유지
	// -> 동일한 상태가 아니면? 예외 펑
	// 3. 기존 예약이 RELEASED -> 현재 Order 기준으로 다시 예약
	// 비정상 시나리오(정합성이 깨짐)
	// 1. RESERVED / RELEASED가 섞여 있음
	// 2. CONFIRMED가 결제 준비 단계에서 발견됨
	// 3. RESERVED 상태인데 현재 주문 수량과 예약 수량이 다름
	public void reserveOrKeep(Order order) {
		// 궁금한게 왜 List 반환은 Optional 반환을 안하나?
		List<InventoryReservation> reservations =
			invReserveRepo.findByOrderIdForUpdate(order.getId());

		// 1. 아직 예약이 없다? -> 최초 결제
		if(reservations.isEmpty()) {
			reserveNew(order);
			return;
		}

		// CONFIRMD 예약 존재 -> 이미 재고 사용 확정이라는 뜻
		// 그런데 다시 payment prepare가 호출됐다는 건 정합성이 깨진 상태다.
		if (reservations.stream()
			.anyMatch(InventoryReservation::isConfirmed)) {

			throw new IllegalStateException(
				"Confirmed inventory reservation exists "
					+ "during payment preparation. "
					+ "orderId=" + order.getId()
			);
		}
		boolean allReserved =
			reservations.stream()
				.allMatch(InventoryReservation::isReserved);

		boolean allReleased =
			reservations.stream()
				.allMatch(InventoryReservation::isReleased);

		// RESERVED와 RELEASED가 섞인 상태는 정합성이 깨진 상태
		if (!allReserved && !allReleased) {
			throw new IllegalStateException(
				"Inventory reservation states are inconsistent. "
					+ "orderId=" + order.getId()
			);
		}

		// 2. 전부 RESERVED 상태라면 현재 주문 내역과 비교하고 예약 진행
		if (allReserved) {
			validateReservedQuantityMatchesOrder(
				order,
				reservations
			);
			return;
		}

		// 3. 이 라인까지 오면 모든 예약이 RELEASED다.
		// 주문 변경 -> 예약이 다 풀린 상태
		// 중간에 다른 사람이 재고를 다 가져갔을 수 있어서 재고 확인 필요
		reserveAgain(
			order,
			reservations
		);
	}


	private void reserveNew(
		Order order
	) {
		Map<Long, Long> qtyByProductId =
			getOrderQtyByProductId(order);

		List<Inventory> inventories =
			findInventoriesForUpdate(
				qtyByProductId.keySet()
			);

		// 재고 검증하기
		validateAvailableStocks(
			inventories,
			qtyByProductId
		);

		/*
		 * 전체 검증이 끝났으므로 실제 예약.
		 */
		for (Inventory inventory : inventories) {

			Long productId =
				inventory.getProduct().getId();

			long qty =
				qtyByProductId.get(productId);

			inventory.reserveStock(qty);

			invReserveRepo.save(
				InventoryReservation.reserve(
					order.getId(),
					inventory.getId(),
					qty
				)
			);
		}
	}

	/**
	 *
	 * order에 있는 정보대로 예약하기
	 */
	private void reserveAgain(
		Order order,
		List<InventoryReservation> reservations
	) {
		Map<Long, Long> qtyByProductId =
			getOrderQtyByProductId(order);

		List<Inventory> inventories =
			findInventoriesForUpdate(
				qtyByProductId.keySet()
			);


		// 기존 예약을 inventoryId로 연결한다.
		Map<Long, InventoryReservation>
			reservationByInventoryId =
			reservations.stream()
				.collect(
					Collectors.toMap(
						InventoryReservation::getInventoryId,
						Function.identity()
					)
				);

		/*
		 * 해제된 사이 재고가 소진됐을 수 있으므로
		 * 반드시 다시 검증한다.
		 */
		validateAvailableStocks(
			inventories,
			qtyByProductId
		);

		/*
		 * 검증 이후 실제 재예약.
		 */
		for (Inventory inventory : inventories) {

			Long productId =
				inventory.getProduct().getId();

			long qty =
				qtyByProductId.get(productId);

			/*
			 * Inventory의 reserved 증가
			 */
			inventory.reserveStock(qty);

			InventoryReservation reservation =
				reservationByInventoryId.get(
					inventory.getId()
				);

			if (reservation == null) {

				/*
				 * 기존 주문에는 없었던 상품.
				 *
				 * 예:
				 * A -> A + B
				 *
				 * B는 기존 reservation row가 없으므로
				 * 새 row를 생성한다.
				 */
				invReserveRepo.save(
					InventoryReservation.reserve(
						order.getId(),
						inventory.getId(),
						qty
					)
				);

				continue;
			}

			/*
			 * 기존 RELEASED row가 있다면
			 * 새로운 수량으로 다시 활성화한다.
			 */
			reservation.reserveAgain(qty);
		}
	}

	/**
	 *
	 * 이미 예약되어 있는 수량이랑 주문 비교 검증
	 */
	private void validateReservedQuantityMatchesOrder(
		Order order,
		List<InventoryReservation> reservations
	) {
		// <productId, 주문 수량> map 작성, order 기반
		Map<Long, Long> requiredQtyByProductId =
			getOrderQtyByProductId(order);

		/*
		 * Reservation은 inventoryId만 가지고 있기 때문에
		 * 해당 Inventory를 통해 productId를 구한다.
		 */
		List<Long> inventoryIds =
			reservations.stream()
				.map(
					InventoryReservation::getInventoryId
				)
				.distinct()
				.sorted()
				.toList();

		List<Inventory> inventories =
			invRepo.findAllByIdIn(inventoryIds);

		if (inventories.size()
			!= inventoryIds.size()) {
			throw new IllegalStateException(
				"Inventory referenced by reservation does not exist. "
					+ "orderId=" + order.getId()
			);
		}

		// <inventoryId, productId> map 작성
		Map<Long, Long> productIdByInventoryId =
			inventories.stream()
				.collect(
					Collectors.toMap(
						Inventory::getId,
						inventory -> inventory.getProduct().getId()
					)
				);

		// <productId, 주문 수량> map 구하기, 이게 진짜 졸라 어렵네
		Map<Long, Long> reservedQtyByProductId =
			reservations.stream()
				.collect(
					Collectors.groupingBy(
						reservation -> {
							Long productId =
								productIdByInventoryId.get(
									reservation
										.getInventoryId()
								);

							if (productId == null) {
								throw new IllegalStateException(
									"Cannot resolve product from inventory. "
										+ "inventoryId="
										+ reservation.getInventoryId()
								);
							}

							return productId;
						},

						Collectors.summingLong(
							InventoryReservation::getQty
						)
					)
				);

		/*
		 * 현재 주문 수량과 실제 예약 수량이 다르다면
		 * 정상적인 상태가 아니다.
		 */
		if (!requiredQtyByProductId.equals(
			reservedQtyByProductId
		)) {

			throw new IllegalStateException(
				"Reserved inventory does not match order. "
					+ "orderId=" + order.getId()
					+ ", required="
					+ requiredQtyByProductId
					+ ", reserved="
					+ reservedQtyByProductId
			);
		}
	}


	/**
	 * 현재 Order가 필요로 하는 상품별 수량.
	 *
	 * OrderProduct가 실제 구매 단위당 1 row이므로
	 * productId별 row 개수를 센다.
	 */
	private Map<Long, Long> getOrderQtyByProductId(
		Order order
	) {
		return order.getOrderProducts()
			.stream()
			.collect(
				Collectors.groupingBy(
					OrderProduct::getProductId,
					Collectors.counting()
				)
			);
	}


	/**
	 * 필요한 Inventory를 일정한 순서로 잠근다.
	 *
	 * 여러 트랜잭션이 같은 Inventory들을
	 * 서로 다른 순서로 잠그면 데드락 가능성이 증가한다.
	 *
	 * 따라서 productId를 정렬해서 조회한다.
	 *
	 * 실제 DB의 lock 획득 순서를 확실하게 맞추려면
	 * Repository 쿼리에도 ORDER BY를 넣는 것이 좋다.
	 */
	private List<Inventory> findInventoriesForUpdate(
		Set<Long> productIds
	) {
		List<Long> sortedProductIds =
			productIds.stream()
				.sorted()
				.toList();

		List<Inventory> inventories =
			invRepo
				.findByProductIdInForUpdate(
					sortedProductIds
				);

		if (inventories.size()
			!= sortedProductIds.size()) {

			throw new IllegalStateException(
				"Some inventories do not exist. "
					+ "requestedProductIds="
					+ sortedProductIds
			);
		}

		return inventories;
	}


	/**
	 * 모든 상품의 재고가 충분한지 확인한다.
	 *
	 * availableStock =
	 * stock - reserved
	 */
	private void validateAvailableStocks(
		List<Inventory> inventories,
		Map<Long, Long> qtyByProductId
	) {
		for (Inventory inventory : inventories) {

			Long productId =
				inventory.getProduct().getId();

			Long qty =
				qtyByProductId.get(productId);

			// 정상적인 경우 반드시 존재해야 한다.
			if (qty == null) {
				throw new IllegalStateException(
					"Requested quantity does not exist. "
						+ "productId=" + productId
				);
			}

			if (inventory.availableStock() < qty) {

				throw BusinessException.of(
					ErrorCode.STOCK_NOT_SUFFICIENT,
					"Insufficient stock. "
						+ "productId=" + productId
						+ ", requestedQty=" + qty
						+ ", availableStock="
						+ inventory.availableStock()
				);
			}
		}
	}
}
