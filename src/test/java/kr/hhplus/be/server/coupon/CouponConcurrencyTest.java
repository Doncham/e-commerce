package kr.hhplus.be.server.coupon;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.apache.commons.lang3.exception.ExceptionUtils.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.FixturePersist;
import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.api.usercoupon.request.UserCouponCreateRequest;
import kr.hhplus.be.server.application.userCoupon.UserCouponService;
import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.coupon.exception.InsufficientCouponStockException;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.coupon.CouponRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;

@SpringBootTest
@AutoConfigureMockMvc
//@Transactional
public class CouponConcurrencyTest {
	@Autowired
	private UserRepository userRepo;
	@Autowired
	private CouponRepository couponRepo;
	@Autowired
	private FixturePersist fixturePersist;
	@Autowired
	private UserCouponService userCouponService;
	@Autowired
	private UserCouponRepository userCouponRepository;


	@Test
	void givenCouponStockOneAndTwoUser_whenIssuesSimultaneously_thenOnlyOneCouponIssued() throws InterruptedException {
		User user1 = fixturePersist.saveAndFlush(userRepo, TestFixture.user());
		User user2 = fixturePersist.saveAndFlush(userRepo, TestFixture.user());
		Coupon coupon = fixturePersist.saveAndFlush(couponRepo, TestFixture.percent10CouponForOneUser(1L));
		UserCouponCreateRequest request1 = UserCouponCreateRequest.of(user1.getId(), coupon.getId());
		UserCouponCreateRequest request2 = UserCouponCreateRequest.of(user2.getId(), coupon.getId());

		int threads = 2;
		ExecutorService pool = Executors.newFixedThreadPool(threads);

		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);

		AtomicInteger success = new AtomicInteger();

		List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

		Runnable task1 = () -> {
			ready.countDown();
			await(start);
			try {
				userCouponService.createUserCoupon(request1);
				success.incrementAndGet();
			} catch (Throwable e) {
				errors.add(e);
			} finally {
				done.countDown();
			}
		};

		Runnable task2 = () -> {
			ready.countDown();
			await(start);
			try {
				userCouponService.createUserCoupon(request2);
				success.incrementAndGet();
			} catch (Throwable e) {
				errors.add(e);
			} finally {
				done.countDown();
			}
		};

		pool.submit(task1);
		pool.submit(task2);

		ready.await();
		start.countDown();
		done.await();
		pool.shutdown();
		Throwable root = rootCause(errors.get(0));
		Long issuedCount = userCouponRepository.countByCouponId(coupon.getId());
		assertEquals(1, issuedCount);

		Coupon reloaded = couponRepo.findById(coupon.getId()).orElseThrow();
		assertEquals(1, reloaded.getIssuedCount());
		assertEquals(1, success.get());
		assertEquals(1, errors.size());
		assertTrue(root instanceof InsufficientCouponStockException);
	}

	@Test
	void givenCouponStock100_whenIssue200Coupon_then100CouponIssuedAnd100ExceptionOccur() throws InterruptedException {
		List<User> users = new ArrayList<>();
		List<UserCouponCreateRequest> requests = new ArrayList<>();
		// Stock이 100개인 쿠폰 생성
		Coupon coupon = fixturePersist.saveAndFlush(couponRepo, TestFixture.percent10CouponForOneUser(100L));

		for(int i = 0; i < 200; i++) {
			User user = fixturePersist.saveAndFlush(userRepo, TestFixture.user());
			users.add(user);
			UserCouponCreateRequest userCouponCreateRequest = UserCouponCreateRequest.of(user.getId(), coupon.getId());
			requests.add(userCouponCreateRequest);
		}

		int threads = 200;
		ExecutorService pool = Executors.newFixedThreadPool(threads);

		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);

		AtomicInteger success = new AtomicInteger();

		List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

		for(int i = 0; i < 200; i++) {
			final int idx = i;
			Runnable task = () -> {
				ready.countDown();
				await(start);
				try {
					userCouponService.createUserCoupon(requests.get(idx));
					success.incrementAndGet();
				} catch (Throwable e) {
					errors.add(e);
				} finally {
					done.countDown();
				}
			};
			pool.submit(task);
		}

		ready.await();
		start.countDown();
		done.await();
		pool.shutdown();

		// 검증
		Coupon c = couponRepo.findById(coupon.getId()).get();
		long issuedCouponCount = userCouponRepository.countByCouponId(coupon.getId());
		assertEquals(100, issuedCouponCount);
		assertEquals(100, c.getIssuedCount());
		assertEquals(100, success.get());
		assertEquals(100, errors.size());
		assertTrue(errors.stream().allMatch(e -> hasCause(rootCause(e), InsufficientCouponStockException.class)));

	}

	private static Throwable rootCause(Throwable t) {
		Throwable cur = t;
		while (cur.getCause() != null && cur.getCause() != cur) {
			cur = cur.getCause();
		}
		return cur;
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
