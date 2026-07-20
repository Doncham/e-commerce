package kr.hhplus.be.server.config.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource {
	@Override
	protected Object determineCurrentLookupKey() {
		// 강제 지정한 값이 있으면 그거 사용
		DataSourceType forced = DataSourceContextHolder.get();
		if (forced != null) {
			//log.info("Routing to forced datasource: {}", forced);
			return forced;
		}

		// 읽기 전용 트랜잭션이면 replica, 아니면 primary
		boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
		DataSourceType result = readOnly ? DataSourceType.REPLICA : DataSourceType.PRIMARY;
		//log.info("Routing to datasource: {}, readOnly={}", result, readOnly);
		return result;
	}
}
