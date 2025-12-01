package kr.hhplus.be.server.cartItem.port.out;

import java.util.List;

import kr.hhplus.be.server.cartItem.CartItem;

public interface CartItemPort {
	List<CartItem> findByCartIdWithProduct(Long cartId);
}
