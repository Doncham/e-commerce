package kr.hhplus.be.server.application;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;

@SpringBootTest
public class Txtest {
	@Autowired
	ApplicationContext applicationContext;
	@Autowired
	OrderRepository orderRepository;

	@Test
	void printTransactionManagers() {
		Map<String, PlatformTransactionManager> managers = applicationContext.getBeansOfType(
			PlatformTransactionManager.class);

		managers.forEach((name, manager) ->
			System.out.println(name +" -> " + manager.getClass()));
	}

	@Transactional
	@Test
	public void test() {
		orderRepository.findById(1L);
	}

}
