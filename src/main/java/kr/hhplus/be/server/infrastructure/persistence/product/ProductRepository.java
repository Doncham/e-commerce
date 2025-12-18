package kr.hhplus.be.server.infrastructure.persistence.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.product.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}
