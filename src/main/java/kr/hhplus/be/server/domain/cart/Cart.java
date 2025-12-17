package kr.hhplus.be.server.domain.cart;


import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import kr.hhplus.be.server.domain.user.User;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class Cart extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(nullable = false)
	private long itemCount = 0;

	@Column(nullable = false)
	private long itemTotalPrice = 0L;

	@OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<CartItem> items = new ArrayList<>();

	@Builder
	public Cart(User user) {
		this.user = user;
	}

	public void reCalcTotals() {
		this.itemCount = items.stream()
			.mapToLong(CartItem::getQty)
			.sum();
		this.itemTotalPrice = items.stream()
			.mapToLong(CartItem::getCurrentTotalPrice)
			.sum();
	}

	public void addItem(CartItem item) {
		// 예외 던져야지 왜 return만 하고 있는데 ㅋㅋㅋ
		if(item.getCart() != null && item.getCart() != this) {
			// 이미 다른 카트에 속함.
			return;
		}
		items.add(item);
		item.initCart(this);
		reCalcTotals();
	}
}
