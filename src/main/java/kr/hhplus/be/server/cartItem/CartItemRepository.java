package kr.hhplus.be.server.cartItem;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
	List<CartItem> findByCartId(Long cartId);

	@Query("SELECT ci "
		+ "FROM CartItem ci "
		+ "JOIN FETCH ci.product WHERE ci.cart.id = :cartId")
	List<CartItem> findByCartIdWithProduct(Long cartId);
}
