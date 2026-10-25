package ctu.core.server.config;

import java.util.List;

public class RemoteServerConfig {
	private String host;
	private int port;
	private String publicHost;
	private int publicPort;
	private ServerType type;
	private List<SystemRange> systemRanges = List.of();

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

	/** The system ids this server runs; empty for a lobby or a shard given none. */
	public List<SystemRange> getSystemRanges() {
		return systemRanges;
	}

	public void setSystemRanges(List<SystemRange> systemRanges) {
		this.systemRanges = List.copyOf(systemRanges);
	}

	public boolean ownsSystem(long systemId) {
		return SystemRange.anyContains(systemRanges, systemId);
	}
}
