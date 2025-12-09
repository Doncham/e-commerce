package kr.hhplus.be.server.application.userCoupon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.api.usercoupon.request.UserCouponCreateRequest;
import kr.hhplus.be.server.api.usercoupon.response.UserCouponCreateResponse;
import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.coupon.exception.CouponExpiredException;
import kr.hhplus.be.server.domain.coupon.exception.UserCouponLimitExceededException;
import kr.hhplus.be.server.domain.coupon.exception.InsufficientCouponStockException;
import kr.hhplus.be.server.domain.coupon.exception.NotFoundCoupon;
import kr.hhplus.be.server.domain.usercoupon.UserCoupon;
import kr.hhplus.be.server.infrastructure.persistence.coupon.CouponRepository;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;

@ExtendWith(MockitoExtension.class)
class UserCouponServiceTest {
	@InjectMocks
	private UserCouponService userCouponService;
	@Mock
	private UserCouponRepository userCouponRepository;
	@Mock
	private CouponRepository couponRepository;
	@Test
	void givenValidUserIdAndCouponId_whenCreateUserCoupon_thenReturnUserCouponCreateResponse(){
		// given
		Long userId = 1L;
		Long couponId = 1L;
		Long userCouponId = 100L;
		UserCouponCreateRequest request = UserCouponCreateRequest.of(userId, couponId);
		UserCoupon userCoupon = createUserCoupon(userId, couponId, userCouponId);
		Coupon couponMock = mock(Coupon.class);

		when(couponRepository.findByIdForUpdate(couponId)).thenReturn(Optional.of(couponMock));
		when(couponMock.isExpired()).thenReturn(false);
		when(couponMock.hasStock()).thenReturn(true);
		when(userCouponRepository.save(any())).thenReturn(userCoupon);
		when(userCouponRepository.countAllByUserIdAndCouponId(userId, couponId)).thenReturn(0L);
		when(couponMock.getIssueLimitPerUser()).thenReturn(5L);

		// when
		UserCouponCreateResponse response = userCouponService.createUserCoupon(request);
		// then
		assertEquals(userId, response.getUserId());
		assertEquals(userCouponId, response.getUserCouponId());
		// 이 테스트를 위해서 UserCouponService에서 시간을 주입받아야하나?
		assertNotNull(response.getIssuedAt());
		assertEquals("ISSUED", response.getStatus().name());
		verify(couponMock).increaseIssuedCount();
	}

	// 없는 쿠폰에 대한 예외
	@Test
	void givenInvalidCouponId_whenCreateUserCoupon_thenThrowException(){
		// given
		Long userId = 1L;
		Long invalidCouponId = 999L;
		UserCouponCreateRequest request = UserCouponCreateRequest.of(userId, invalidCouponId);

		when(couponRepository.findByIdForUpdate(invalidCouponId)).thenReturn(Optional.empty());

		// when & then
		assertThrows(NotFoundCoupon.class, () -> {
			userCouponService.createUserCoupon(request);
		});
	}

	// 쿠폰 만료에 대한 예외
	@Test
	void givenExpiredCoupon_whenCreateUserCoupon_thenThrowException() {
		// given
		Long userId = 1L;
		Long couponId = 1L;
		UserCouponCreateRequest request = UserCouponCreateRequest.of(userId, couponId);
		Coupon couponMock = mock(Coupon.class);

		when(couponRepository.findByIdForUpdate(couponId)).thenReturn(Optional.of(couponMock));
		when(couponMock.isExpired()).thenReturn(true);
		when(userCouponRepository.countAllByUserIdAndCouponId(userId, couponId)).thenReturn(0L);
		when(couponMock.getIssueLimitPerUser()).thenReturn(5L);

		// when & then
		assertThrows(CouponExpiredException.class, () -> {
			userCouponService.createUserCoupon(request);
		});

		verify(userCouponRepository, never()).save(any());
	}

	// 쿠폰 발급 한도 초과에 대한 예외
	@Test
	void givenCouponLimitExceeded_whenCreateUserCoupon_thenThrowException() {
		// given
		Long userId = 1L;
		Long couponId = 1L;
		UserCouponCreateRequest request = UserCouponCreateRequest.of(userId, couponId);
		Coupon couponMock = mock(Coupon.class);

		when(couponRepository.findByIdForUpdate(couponId)).thenReturn(Optional.of(couponMock));
		when(couponMock.isExpired()).thenReturn(false);
		when(couponMock.hasStock()).thenReturn(false);
		when(userCouponRepository.countAllByUserIdAndCouponId(userId, couponId)).thenReturn(0L);
		when(couponMock.getIssueLimitPerUser()).thenReturn(5L);
		// when & then
		assertThrows(InsufficientCouponStockException.class, () -> {
			userCouponService.createUserCoupon(request);
		});
	}

	// 사용자당 쿠폰 발급 한도 초과에 대한 예외
	@Test
	void givenUserCouponLimitExceeded_whenCreateUserCoupon_thenThrowException() {
		// given
		Long userId = 1L;
		Long couponId = 1L;
		UserCouponCreateRequest request = UserCouponCreateRequest.of(userId, couponId);
		Coupon couponMock = mock(Coupon.class);

		when(couponRepository.findByIdForUpdate(couponId)).thenReturn(Optional.of(couponMock));
		when(userCouponRepository.countAllByUserIdAndCouponId(userId, couponId)).thenReturn(5L);
		when(couponMock.getIssueLimitPerUser()).thenReturn(5L);

		// when & then
		assertThrows(UserCouponLimitExceededException.class, () -> {
			userCouponService.createUserCoupon(request);
		});
	}



	private UserCoupon createUserCoupon(Long userId, Long couponId, Long userCouponId) {
		UserCoupon userCoupon = UserCoupon.createUserCoupon(userId, couponId);
		ReflectionTestUtils.setField(userCoupon, "id", userCouponId);
		return userCoupon;
	}

}