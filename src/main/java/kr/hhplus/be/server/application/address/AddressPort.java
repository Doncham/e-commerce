package kr.hhplus.be.server.application.address;

import kr.hhplus.be.server.domain.address.Address;

public interface AddressPort {
	Address loadAddress(Long addressId);
}
