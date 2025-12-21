package kr.hhplus.be.server.infrastructure.persistence.cartItem;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.cartItem.CartItem;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
	List<CartItem> findByCartId(Long cartId);

	@Query("SELECT ci "
		+ "FROM CartItem ci "
		+ "JOIN FETCH ci.product WHERE ci.cart.id = :cartId")
	List<CartItem> findByCartIdWithProduct(Long cartId);
}
