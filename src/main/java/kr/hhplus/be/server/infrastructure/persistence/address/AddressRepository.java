package kr.hhplus.be.server.infrastructure.persistence.address;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.address.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
}
