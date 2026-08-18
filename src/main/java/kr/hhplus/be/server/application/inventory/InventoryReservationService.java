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
	// 3. 기존 예약이 RELEASED -> 현재 Order 기준으로 다시 예약
	// 비정상 시나리오(정합성이 깨짐)
	// 1. CONFIRMED가 결제 준비 단계에서 발견됨
	// 2.
	public void reserveOrKeep(Order order) {

		List<InventoryReservation> reservations =
			invReserveRepo.findByOrderIdForUpdate(
				order.getId()
			);

		// 1. 최초 결제 준비
		if (reservations.isEmpty()) {
			reserveNew(order);
			return;
		}

		// 결제 준비 단계에서 CONFIRMED 예약이 존재하면 비정상
		if (reservations.stream()
			.anyMatch(InventoryReservation::isConfirmed)) {

			throw new IllegalStateException(
				"Confirmed inventory reservation exists "
					+ "during payment preparation. "
					+ "orderId=" + order.getId()
			);
		}

		Map<Long, Long> requiredQtyByProductId =
			getOrderQtyByProductId(order);

		/*
		 * 과거 RELEASED 예약은 무시하고,
		 * 현재 살아 있는 RESERVED 예약만 비교한다.
		 */
		Map<Long, Long> reservedQtyByProductId =
			reservations.stream()
				.filter(InventoryReservation::isReserved)
				.collect(Collectors.toMap(
					InventoryReservation::getProductId,
					InventoryReservation::getQty
				));


		// 2. 기존 예약과 현재 주문 상태가 같음 -> return
		if (requiredQtyByProductId
			.equals(reservedQtyByProductId)) {
			return;
		}

		// 여기까지 왔다면 기존 예약과 현재 주문 상태가 다른거임.
		// 주문 상태 변경 -> 싹 다 RELEASED인데 RESERVED된 예약이 존재한다면 정합성 깨진 것.
		if (!reservedQtyByProductId.isEmpty()) {
			throw new IllegalStateException(
				"Reserved inventory does not match order. "
					+ "orderId=" + order.getId()
					+ ", required="
					+ requiredQtyByProductId
					+ ", reserved="
					+ reservedQtyByProductId
			);
		}

		// 주문 변경으로 기존 예약이 전부 RELEASE된 상황이므로
		// 현재 Order 기준으로 다시 예약한다.
		reserveAgain(order, reservations);
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
					qty,
					inventory.getProduct().getId()
				)
			);
		}
	}

	/**
	 *
	 * order에 있는 정보대로 예약하기
	 * 1. 새로운 상품 예약 저장
	 * 2. 기존 상품은 예약 갱신
	 */
	private void reserveAgain(
		Order order,
		List<InventoryReservation> reservations
	) {
		/*
		 * 현재 주문 기준 필요한 수량
		 *
		 * B -> 2
		 * C -> 1
		 */
		Map<Long, Long> qtyByProductId =
			getOrderQtyByProductId(order);

		List<Inventory> inventories =
			findInventoriesForUpdate(
				qtyByProductId.keySet()
			);

		/*
		 * 과거 예약을 productId 기준으로 찾기 쉽게 변경.
		 *
		 * A -> RELEASED reservation
		 * B -> RELEASED reservation
		 */
		Map<Long, InventoryReservation>
			reservationByProductId =
			reservations.stream()
				.collect(Collectors.toMap(
					InventoryReservation::getProductId,
					Function.identity()
				));

		/*
		 * 기존 예약을 RELEASE한 이후
		 * 다른 주문이 재고를 가져갔을 수 있으므로
		 * 현재 시점 재고를 다시 검증한다.
		 */
		validateAvailableStocks(
			inventories,
			qtyByProductId
		);

		/*
		 * 검증이 모두 끝난 뒤 실제 예약.
		 */
		for (Inventory inventory : inventories) {

			Long productId =
				inventory.getProduct().getId();

			long qty =
				qtyByProductId.get(productId);

			// 실제 Inventory reserved 증가
			inventory.reserveStock(qty);

			InventoryReservation reservation =
				reservationByProductId.get(productId);

			// 기존에 있던 예약과 처음 예약하는 상품으로 나뉨.
			if (reservation == null) {
				// 기존 A, B
				// 현재 B, C
				// C는 reservation row 자체가 없으므로 생성.
				// B는 reserveAgain()으로 다시 예약
				invReserveRepo.save(
					InventoryReservation.reserve(
						order.getId(),
						inventory.getId(),
						qty,
						productId
					)
				);

				continue;
			}

			/*
			 * 기존 주문에도 있었던 상품.
			 *
			 * 주문 변경 과정에서 RELEASED되어 있으므로
			 * 기존 row를 재사용한다.
			 */
			reservation.reserveAgain(qty);
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
