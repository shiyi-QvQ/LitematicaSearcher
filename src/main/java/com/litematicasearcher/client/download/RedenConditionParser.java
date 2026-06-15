package com.litematicasearcher.client.download;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RedenConditionParser {
	private static final Pattern MIN_PATTERN = Pattern.compile("min\\((-?\\d+)\\)");
	private static final Pattern MAX_PATTERN = Pattern.compile("max\\((-?\\d+)\\)");
	private static final Pattern MOD_PATTERN = Pattern.compile("mod\\((-?\\d+),\\s*(-?\\d+)\\)");

	private RedenConditionParser() {
	}

	public static AxisConstraints parse(List<String> rawConditions) {
		int min = Integer.MIN_VALUE;
		int max = Integer.MAX_VALUE;
		List<ModConstraint> modConstraints = new ArrayList<>();

		for (String rawCondition : rawConditions) {
			String condition = rawCondition.trim();
			Matcher minMatcher = MIN_PATTERN.matcher(condition);
			Matcher maxMatcher = MAX_PATTERN.matcher(condition);
			Matcher modMatcher = MOD_PATTERN.matcher(condition);

			if (minMatcher.matches()) {
				min = Math.max(min, Integer.parseInt(minMatcher.group(1)));
			} else if (maxMatcher.matches()) {
				max = Math.min(max, Integer.parseInt(maxMatcher.group(1)));
			} else if (modMatcher.matches()) {
				int step = Integer.parseInt(modMatcher.group(1));
				int offset = Integer.parseInt(modMatcher.group(2));

				if (step != 0 || offset != 0) {
					modConstraints.add(new ModConstraint(Math.abs(step), offset));
				}
			}
		}

		return new AxisConstraints(min, max, List.copyOf(modConstraints));
	}

	public record AxisConstraints(int min, int max, List<ModConstraint> modConstraints) {
		public ValidationResult validate(int value) {
			if (value < min) {
				return ValidationResult.tooSmall(min);
			}

			if (value > max) {
				return ValidationResult.tooLarge(max);
			}

			for (ModConstraint constraint : modConstraints) {
				if (!constraint.matches(value)) {
					return ValidationResult.badStep(constraint.step(), constraint.offset());
				}
			}

			return ValidationResult.ok();
		}
	}

	public record ModConstraint(int step, int offset) {
		public boolean matches(int value) {
			if (step == 0) {
				return false;
			}

			return Math.floorMod(value - offset, step) == 0;
		}
	}

	public record ValidationResult(boolean valid, ErrorType errorType, int firstValue, int secondValue) {
		public static ValidationResult ok() {
			return new ValidationResult(true, ErrorType.NONE, 0, 0);
		}

		public static ValidationResult tooSmall(int min) {
			return new ValidationResult(false, ErrorType.TOO_SMALL, min, 0);
		}

		public static ValidationResult tooLarge(int max) {
			return new ValidationResult(false, ErrorType.TOO_LARGE, max, 0);
		}

		public static ValidationResult badStep(int step, int offset) {
			return new ValidationResult(false, ErrorType.BAD_STEP, step, offset);
		}
	}

	public enum ErrorType {
		NONE,
		TOO_SMALL,
		TOO_LARGE,
		BAD_STEP
	}
}
