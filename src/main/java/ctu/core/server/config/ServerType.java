package ctu.core.server.config;

public enum ServerType {
	LOBBY(0), GAME(1);

	private final int value;

	ServerType(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}

	public static ServerType fromValue(int value) {
		for (ServerType type : values()) {
			if (type.value == value) {
				return type;
			}
		}
		return GAME;
	}
}
