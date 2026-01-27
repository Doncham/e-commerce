package kr.hhplus.be.server.infrastructure.persistence.orderproduct;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.application.product.ProductSoldQtyDTO;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;

@Repository
public interface OrderProductRepository extends JpaRepository<OrderProduct, Long> {
	// 50개 조회
	// product의 deletedAt을 보고 조회해야함.(이거 진자 어케하는거지?)
	// orderProduct에 craetedAt 필드 추가 check
	// 매개변수로 from, to를 받아서 해당 기간에 주문된 내역으로만 평가
	// OrderStatus가 PAID인 주문만 내역으로 평가
	@Query("""
  		select new kr.hhplus.be.server.application.product.ProductSoldQtyDTO(op.productId, SUM(op.qty)) 
		from OrderProduct as op
		join op.order o
		join Product p on p.id = op.productId
		where o.createdAt >= :from
			and o.createdAt < :to
			and o.status = :status
			and p.deletedAt is null
			and p.isActive = true
		group by op.productId
		order by sum(op.qty) desc
		""")
	List<ProductSoldQtyDTO> findPopularProduct(
		@Param("from") LocalDateTime from,
		@Param("to") LocalDateTime to,
		@Param("status") OrderStatus status,
		Pageable pageRequest
	);

}
