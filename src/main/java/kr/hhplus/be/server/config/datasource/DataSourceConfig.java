package kr.hhplus.be.server.config.datasource;

import java.util.HashMap;

import javax.sql.DataSource;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

@Configuration
public class DataSourceConfig {
	@Bean
	@ConfigurationProperties("spring.datasource.primary")
	public DataSourceProperties primaryDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	public DataSource primaryDataSource() {
		return primaryDataSourceProperties()
			.initializeDataSourceBuilder()
			.build();
	}

	@Bean
	@ConfigurationProperties("spring.datasource.replica")
	public DataSourceProperties replicaDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	public DataSource replicaDataSource() {
		return replicaDataSourceProperties()
			.initializeDataSourceBuilder()
			.build();
	}

	@Bean
	public DataSource routingDataSource(
		@Qualifier("primaryDataSource") DataSource primaryDataSource,
		@Qualifier("replicaDataSource") DataSource replicaDataSource
	) {
		HashMap<Object, Object> dataSourceMap = new HashMap<>();
		dataSourceMap.put(DataSourceType.PRIMARY, primaryDataSource);
		dataSourceMap.put(DataSourceType.REPLICA, replicaDataSource);

		// AbstractRoutingDataSource, readOnly면 DataSourceType.REPLICA로 보내는 설정
		RoutingDataSource routingDataSource = new RoutingDataSource();
		routingDataSource.setTargetDataSources(dataSourceMap);
		routingDataSource.setDefaultTargetDataSource(primaryDataSource);
		routingDataSource.afterPropertiesSet();

		return routingDataSource;
	}

	@Bean
	@Primary
	// JPA는 routingDataSource만 보게한다.
	public DataSource dataSource(@Qualifier("routingDataSource") DataSource routingDataSource) {
		// 읽기/쓰기 라우팅은 트랜잭션 정보가 결정된 뒤에 DB를 골라야 해.
		// LazyConnectionDataSourceProxy를 두면 실제로 SQL 나갈 때까지 connection 획득을 늦춰준다고 생각.
		return new LazyConnectionDataSourceProxy(routingDataSource);
	}

	@Bean
	@Primary
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(
		EntityManagerFactoryBuilder builder,
		@Qualifier("dataSource") DataSource dataSource
	) {
		return builder
			.dataSource(dataSource)
			.packages("com.example.demo.domain")
			.persistenceUnit("default")
			.build();
	}
	@Bean
	@Primary
	public PlatformTransactionManager transactionManager(
		@Qualifier("entityManagerFactory") EntityManagerFactory emf
	) {
		return new JpaTransactionManager(emf);
	}


}
