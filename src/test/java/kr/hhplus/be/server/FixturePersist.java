package kr.hhplus.be.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FixturePersist {
	@Transactional
	public <T, ID> T save(JpaRepository<T, ID> repo, T entity) {
		return repo.save(entity);
	}

	@Transactional
	public <T, ID> T saveAndFlush(JpaRepository<T, ID> repo, T entity) {
		T saved = repo.save(entity);
		repo.flush();
		return saved;
	}
}
