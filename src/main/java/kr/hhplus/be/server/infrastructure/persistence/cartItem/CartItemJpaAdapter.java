package kr.hhplus.be.server.infrastructure.persistence.cartItem;

import java.util.List;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.application.cartItem.CartItemPort;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CartItemJpaAdapter implements CartItemPort {
	private final CartItemRepository cartItemRepository;
	@Override
	public List<CartItem> findByCartIdWithProduct(Long cartId) {
		return cartItemRepository.findByCartIdWithProduct(cartId);
	}
}
