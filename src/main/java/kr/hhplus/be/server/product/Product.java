package kr.hhplus.be.server.product;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class Product extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false)
	private String name;
	@Column(nullable = false, length = 1000)
	private String description;
	@Column(nullable = false)
	private Long price;
	@Column(nullable = false)
	private boolean isActive = true;
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;


	private Product(String name, String description, Long price) {
		this.name = name;
		this.description = description;
		this.price = price;
	}

	public static Product createProduct(String name, String description, Long price) {
		return new Product(name, description, price);
	}
}
