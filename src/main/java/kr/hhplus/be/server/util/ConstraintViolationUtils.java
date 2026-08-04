package kr.hhplus.be.server.util;

import org.hibernate.exception.ConstraintViolationException;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ConstraintViolationUtils {
	public static boolean hasConstraint(
		Throwable throwable,
		String expectedConstraintName
	) {
		Throwable current = throwable;

		while (current != null) {
			if (current instanceof ConstraintViolationException exception) {
				String actualConstraintName =
					exception.getConstraintName();

				return actualConstraintName != null
					&& actualConstraintName.equalsIgnoreCase(
					expectedConstraintName
				);
			}

			current = current.getCause();
		}

		return false;
	}
}
