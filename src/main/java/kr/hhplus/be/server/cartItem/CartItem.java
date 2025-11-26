package kr.hhplus.be.server.cartItem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.hhplus.be.server.cart.Cart;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import kr.hhplus.be.server.product.Product;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
@Table(
	uniqueConstraints = @UniqueConstraint(
		name = "ux_cart_product_per_cart",
		columnNames = {"cart_id", "product_id"}
	)
)
public class CartItem extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "cart_id", nullable = false)
	private Cart cart;

	// 여러 장바구니의 수많은 CartItem이 동일 Product를 참조한다.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private int qty;

	@Builder
	public CartItem(Cart cart, Product product, int qty) {
		this.cart = cart;
		this.product = product;
		this.qty = qty;
	}

	public long getCurrentUnitPrice() {
		return product.getPrice();
	}

	public long getCurrentTotalPrice() {
		return product.getPrice() * qty;
	}

	public void initCart(Cart cart) {
		this.cart = cart;
	}
}
