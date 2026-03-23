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
		+ "JOIN FETCH ci.product WHERE ci.id = :cartItemId")
	CartItem findByCartItemIdWithProduct(Long cartItemId);

	@Query("""
		select ci
		from CartItem ci
		join fetch ci.product p
		join fetch ci.cart c
		join fetch c.user u
		where ci.id in :cartItemIds
	"""
	)
	List<CartItem> findAllByIdInWithProductCartUser(List<Long> cartItemIds);
}
