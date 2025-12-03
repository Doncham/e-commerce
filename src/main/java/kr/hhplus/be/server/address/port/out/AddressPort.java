package kr.hhplus.be.server.address.port.out;

import kr.hhplus.be.server.address.Address;

public interface AddressPort {
	Address loadAddress(Long addressId);
}
