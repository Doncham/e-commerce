package kr.hhplus.be.server.cartItem.adapter;

import java.util.List;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.cartItem.CartItem;
import kr.hhplus.be.server.cartItem.CartItemRepository;
import kr.hhplus.be.server.cartItem.port.out.CartItemPort;
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
