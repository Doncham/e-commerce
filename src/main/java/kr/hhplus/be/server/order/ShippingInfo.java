package kr.hhplus.be.server.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import kr.hhplus.be.server.address.Address;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor
@Getter
public class ShippingInfo {
	@Column(name = "shipping_receiver", nullable = false, length = 100)
	private String receiver;
	@Column(name = "shipping_phone", nullable = false, length = 20)
	private String phoneNumber;
	@Column(name = "shipping_zipcode", nullable = false, length = 5)
	private String zipcode;
	@Column(name = "shipping_address", nullable = false, length = 200)
	private String address;

	public ShippingInfo(Address address) {
		this.receiver = address.getReceiver();
		this.phoneNumber = address.getPhoneNumber();
		this.zipcode = address.getZipcode();
		this.address = address.getAddress();
	}

}
