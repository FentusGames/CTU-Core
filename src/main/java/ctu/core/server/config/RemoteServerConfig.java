package ctu.core.server.config;

public class RemoteServerConfig {
	private String host;
	private int port;
	private String publicHost;
	private int publicPort;
	private ServerType type;
	private Long systemRangeStart;
	private Long systemRangeEnd;

	public RemoteServerConfig() {
	}

	public RemoteServerConfig(String host, int port, ServerType type) {
		this.host = host;
		this.port = port;
		this.publicHost = host;
		this.publicPort = port;
		this.type = type;
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

	public ServerType getType() {
		return type;
	}

	public void setType(ServerType type) {
		this.type = type;
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

	public boolean ownsSystem(long systemId) {
		if (systemRangeStart == null || systemRangeEnd == null) {
			return false;
		}
		return systemId >= systemRangeStart && systemId <= systemRangeEnd;
	}
}
