package ctu.core.server.config;

import java.util.HashMap;
import java.util.Map;

public class ServerConfig {
	private String serverId;
	private ServerType serverType = ServerType.GAME;
	private String host = "0.0.0.0";
	private int port = 29901;
	private String publicHost;
	private int publicPort;
	private Long systemRangeStart;
	private Long systemRangeEnd;
	private Map<String, RemoteServerConfig> servers = new HashMap<>();
	private String transferTokenSecret;
	private int transferTokenExpirySeconds = 30;
	private int debugPort = 0;
	private Integer nodeId;
	private String databaseUrl;
	private String databaseUsername;
	private String databasePassword;

	public ServerConfig() {
	}

	public ServerConfig applyArgs(ServerArgs args) {
		if (args.getServerId() != null) {
			this.serverId = args.getServerId();
		}
		if (args.getPort() != null) {
			this.port = args.getPort();
		}
		if (args.hasSystemRange()) {
			this.systemRangeStart = args.getSystemRangeStart();
			this.systemRangeEnd = args.getSystemRangeEnd();
		}
		if (args.getPublicHost() != null) {
			this.publicHost = args.getPublicHost();
		}
		if (args.getPublicPort() != null) {
			this.publicPort = args.getPublicPort();
		}
		return this;
	}

	public String findServerForSystem(long systemId) {
		for (Map.Entry<String, RemoteServerConfig> entry : servers.entrySet()) {
			if (entry.getValue().ownsSystem(systemId)) {
				return entry.getKey();
			}
		}
		return null;
	}

	public String getServerId() {
		return serverId;
	}

	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	public ServerType getServerType() {
		return serverType;
	}

	public void setServerType(ServerType serverType) {
		this.serverType = serverType;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getPublicHost() {
		return publicHost != null ? publicHost : host;
	}

	public void setPublicHost(String publicHost) {
		this.publicHost = publicHost;
	}

	public int getPublicPort() {
		return publicPort > 0 ? publicPort : port;
	}

	public void setPublicPort(int publicPort) {
		this.publicPort = publicPort;
	}

	public Long getSystemRangeStart() {
		return systemRangeStart;
	}

	public void setSystemRangeStart(Long systemRangeStart) {
		this.systemRangeStart = systemRangeStart;
	}

	public Long getSystemRangeEnd() {
		return systemRangeEnd;
	}

	public void setSystemRangeEnd(Long systemRangeEnd) {
		this.systemRangeEnd = systemRangeEnd;
	}

	public Map<String, RemoteServerConfig> getServers() {
		return servers;
	}

	public void setServers(Map<String, RemoteServerConfig> servers) {
		this.servers = servers;
	}

	public void addServer(String serverId, RemoteServerConfig config) {
		this.servers.put(serverId, config);
	}

	public RemoteServerConfig getServer(String serverId) {
		return servers.get(serverId);
	}

	public String getTransferTokenSecret() {
		return transferTokenSecret;
	}

	public void setTransferTokenSecret(String transferTokenSecret) {
		this.transferTokenSecret = transferTokenSecret;
	}

	public int getTransferTokenExpirySeconds() {
		return transferTokenExpirySeconds;
	}

	public void setTransferTokenExpirySeconds(int transferTokenExpirySeconds) {
		this.transferTokenExpirySeconds = transferTokenExpirySeconds;
	}

	public boolean ownsSystem(long systemId) {
		if (systemRangeStart == null || systemRangeEnd == null) {
			return false;
		}
		return systemId >= systemRangeStart && systemId <= systemRangeEnd;
	}

	public boolean isLobby() {
		return serverType == ServerType.LOBBY;
	}

	public boolean isGame() {
		return serverType == ServerType.GAME;
	}

	public int getDebugPort() {
		return debugPort;
	}

	public void setDebugPort(int debugPort) {
		this.debugPort = debugPort;
	}

	/**
	 * Node id for Snowflake id generation (0..8191). An explicit {@code server.nodeId}
	 * wins; otherwise it derives from the trailing number of the server id:
	 * {@code shard-N} is N and {@code lobby-N} is N-1 (lobby-1 is 0). Lobbies never mint
	 * runtime ids, so a lobby overlapping a shard is harmless; game shards must differ.
	 */
	public int getNodeId() {
		if (nodeId != null) {
			return nodeId;
		}
		int trailing = trailingNumber(serverId);
		if (trailing < 0) {
			throw new IllegalStateException(
					"server.nodeId is not set and serverId '" + serverId + "' has no trailing number");
		}
		return serverType == ServerType.LOBBY ? trailing - 1 : trailing;
	}

	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	private static int trailingNumber(String text) {
		if (text == null) {
			return -1;
		}
		int end = text.length();
		int start = end;
		while (start > 0 && Character.isDigit(text.charAt(start - 1))) {
			start--;
		}
		if (start == end) {
			return -1;
		}
		return Integer.parseInt(text.substring(start, end));
	}

	public String getDatabaseUrl() {
		return databaseUrl;
	}

	public void setDatabaseUrl(String databaseUrl) {
		this.databaseUrl = databaseUrl;
	}

	public String getDatabaseUsername() {
		return databaseUsername;
	}

	public void setDatabaseUsername(String databaseUsername) {
		this.databaseUsername = databaseUsername;
	}

	public String getDatabasePassword() {
		return databasePassword;
	}

	public void setDatabasePassword(String databasePassword) {
		this.databasePassword = databasePassword;
	}
}
