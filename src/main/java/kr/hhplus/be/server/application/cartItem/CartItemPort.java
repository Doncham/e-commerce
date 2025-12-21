package kr.hhplus.be.server.application.cartItem;

import java.util.List;

import kr.hhplus.be.server.domain.cartItem.CartItem;

public interface CartItemPort {
	List<CartItem> findByCartIdWithProduct(Long cartId);
}
