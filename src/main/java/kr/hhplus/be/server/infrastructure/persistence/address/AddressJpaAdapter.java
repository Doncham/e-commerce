package kr.hhplus.be.server.infrastructure.persistence.address;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.application.address.AddressPort;
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
