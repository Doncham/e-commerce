package kr.hhplus.be.server.config.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataSourceRoutingAspect {
	@Around("@annotation(UsePrimary) || @within(UsePrimary)")
	public Object routeToPrimary(ProceedingJoinPoint joinPoint) throws Throwable {
		try {
			DataSourceContextHolder.set(DataSourceType.PRIMARY);
			return joinPoint.proceed();
		} finally {
			DataSourceContextHolder.clear();
		}
	}
}
