package edu.mit.media.immersion.db;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum AuthStatus {
	PENDING(1), AUTHORIZED(2), FAILED(3);

	private static final Map<Integer, AuthStatus> map = new HashMap<Integer, AuthStatus>();
	static {
		for (AuthStatus s : EnumSet.allOf(AuthStatus.class)) {
			map.put(s.getValue(), s);
		}
	}

	private int value;

	private AuthStatus(int value) {
		this.value = value;
	}

	public static AuthStatus fromValue(int value) {
		return map.get(value);
	}

	public int getValue() {
		return value;
	}
}
