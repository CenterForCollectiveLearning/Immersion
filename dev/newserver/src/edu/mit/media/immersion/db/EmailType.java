package edu.mit.media.immersion.db;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum EmailType {
	GMAIL(1), EXCHANGE(2), HOTMAIL(3), YAHOO(4);

	private static final Map<Integer, EmailType> map = new HashMap<Integer, EmailType>();
	static {
		for (EmailType s : EnumSet.allOf(EmailType.class)) {
			map.put(s.getValue(), s);
		}
	}

	private int value;

	private EmailType(int value) {
		this.value = value;
	}

	public static EmailType fromValue(int value) {
		return map.get(value);
	}

	public int getValue() {
		return value;
	}
}
