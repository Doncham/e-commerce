package kr.hhplus.be.server.infrastructure.persistence.orderproduct;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import kr.hhplus.be.server.application.product.ProductSoldQtyDTO;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.orderproduct.OrderProductStatus;

@Repository
public interface OrderProductRepository extends JpaRepository<OrderProduct, Long> {
	// 50개 조회
	// product의 deletedAt을 보고 조회해야함.(이거 진자 어케하는거지?)
	// orderProduct에 craetedAt 필드 추가 check
	// 매개변수로 from, to를 받아서 해당 기간에 주문된 내역으로만 평가
	// OrderStatus가 PAID인 주문만 내역으로 평가
	@Query("""
  		select new kr.hhplus.be.server.application.product.ProductSoldQtyDTO(op.productId, COUNT(op.productId)) 
		from OrderProduct as op
		join op.order o
		join Product p on p.id = op.productId
		where o.createdAt >= :from
			and o.createdAt < :to
			and o.status = :status
			and p.deletedAt is null
			and p.isActive = true
		group by op.productId
		order by count (op.productId) desc
		""")
	List<ProductSoldQtyDTO> findPopularProduct(
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to,
		@Param("status") OrderStatus status,
		Pageable pageRequest
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select op 
		from OrderProduct op
		where op.id in :orderProductIds
""")
	List<OrderProduct> findByIds(@Param("orderProductIds") List<Long> orderProductIds);

	@Query("""
		select op
		from OrderProduct op
		where op.order.id = :orderId and op.status = :status 
""")
	List<OrderProduct> findCancelableByOrderId(@Param("orderId") Long orderId,
		@Param("status") OrderProductStatus status);
}
