package kr.hhplus.be.server.api.orchestrator;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import kr.hhplus.be.server.api.exception.InvalidInternalTokenException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/internal/db-pool")
@Profile("!prod")
public class DbPoolAdminController {
	private static final String INTERNAL_TOKEN =  "change-me-secret";

	private final DataSource writeDataSource;
	private final DataSource readDataSource;

	public DbPoolAdminController(
		@Qualifier("primaryDataSource") DataSource writeDataSource,
		@Qualifier("replicaDataSource") DataSource readDataSource)
	{
		this.writeDataSource = writeDataSource;
		this.readDataSource = readDataSource;
	}

	@PostMapping("/evict/write")
	public ResponseEntity<Void> evictWrite(
		@RequestHeader("X-Internal-Token") String token
	) {
		validateToken(token);

		evictHikariPool("writeDataSource", writeDataSource);

		return ResponseEntity.noContent().build();
	}
	private void validateToken(String token) {
		if (!INTERNAL_TOKEN.equals(token)) {
			throw new InvalidInternalTokenException("token is invalid");
		}
	}

	private void evictHikariPool(String name, DataSource dataSource) {
		if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
			throw new IllegalStateException(
				name + " is not HikariDataSource. Actual type = " + dataSource.getClass().getName()
			);
		}

		HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

		if (poolMXBean == null) {
			throw new IllegalStateException(name + " HikariPoolMXBean is null");
		}

		log.warn("Soft evict Hikari connections. poolName={}", hikariDataSource.getPoolName());

		poolMXBean.softEvictConnections();
	}
}
