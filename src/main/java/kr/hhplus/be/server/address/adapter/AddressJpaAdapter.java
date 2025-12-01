package kr.hhplus.be.server.address.adapter;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.address.Address;
import kr.hhplus.be.server.address.AddressRepository;
import kr.hhplus.be.server.address.port.out.AddressPort;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Repository
public class AddressJpaAdapter implements AddressPort {
	private final AddressRepository addressRepository;
	@Override
	public Address loadAddress(Long addressId) {
		return addressRepository.findById(addressId).orElseThrow(() -> new IllegalArgumentException("Invalid address ID"));
	}
}
