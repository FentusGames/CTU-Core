package ctu.core.server.config;

public class ServerArgs {
	private Long systemRangeStart;
	private Long systemRangeEnd;
	private Integer port;
	private String serverId;
	private String configFile;
	private String publicHost;
	private Integer publicPort;

	public static ServerArgs parse(String[] args) {
		ServerArgs result = new ServerArgs();

		for (String arg : args) {
			if (arg.startsWith("systems=")) {
				String[] parts = arg.substring(8).split(",");
				if (parts.length == 2) {
					result.systemRangeStart = Long.parseLong(parts[0].trim());
					result.systemRangeEnd = Long.parseLong(parts[1].trim());
				}
			} else if (arg.startsWith("port=")) {
				result.port = Integer.parseInt(arg.substring(5).trim());
			} else if (arg.startsWith("serverId=")) {
				result.serverId = arg.substring(9).trim();
			} else if (arg.startsWith("config=")) {
				result.configFile = arg.substring(7).trim();
			} else if (arg.startsWith("publicHost=")) {
				result.publicHost = arg.substring(11).trim();
			} else if (arg.startsWith("publicPort=")) {
				result.publicPort = Integer.parseInt(arg.substring(11).trim());
			}
		}

		return result;
	}

	public Long getSystemRangeStart() {
		return systemRangeStart;
	}

	public Long getSystemRangeEnd() {
		return systemRangeEnd;
	}

	public Integer getPort() {
		return port;
	}

	public String getServerId() {
		return serverId;
	}

	public String getConfigFile() {
		return configFile;
	}

	public String getPublicHost() {
		return publicHost;
	}

	public Integer getPublicPort() {
		return publicPort;
	}

	public boolean hasSystemRange() {
		return systemRangeStart != null && systemRangeEnd != null;
	}
}
