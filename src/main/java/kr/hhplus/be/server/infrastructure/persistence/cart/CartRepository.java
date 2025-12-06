package kr.hhplus.be.server.infrastructure.persistence.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.cart.Cart;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
}
