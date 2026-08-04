package kr.hhplus.be.server.infrastructure.persistence.address;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.address.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
	@Query("""
    select a
    from Address a
    where a.id = :addressId
      and a.user.id = :userId
""")
	Optional<Address> findByIdAndUserId(
		Long addressId,
		Long userId
	);
}
