package kr.hhplus.be.server.domain.address;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import kr.hhplus.be.server.domain.user.User;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class Address extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	@Column(nullable = false, length = 100)
	private String receiver;
	@Column(nullable = false, name = "phone_number", length = 20)
	private String phoneNumber;
	@Column(nullable = false, length = 5) // 우편 번호는 5자리
	private String zipcode; // 우편 번호
	// roadAddress(도로명 주소), detailAddress(상세주소(동/호)) 분리 x
	@Column(nullable = false, length = 200)
	private String address;
	private String memo;
	private boolean isDefault;
	@Builder
	public Address(User user, String receiver, String phoneNumber, String zipcode, String address, String memo,
		boolean isDefault) {
		this.user = user;
		this.receiver = receiver;
		this.phoneNumber = phoneNumber;
		this.zipcode = zipcode;
		this.address = address;
		this.memo = memo;
		this.isDefault = isDefault;
	}
}
