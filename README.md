# CTU-Core

CTU-Core is a Java client-server application that allows for simple communication between clients and servers. This application comes with advanced encryption methods, compression, and other features.

## Installation

To get started with CTU-Core, follow these steps:

1. Clone the repository to your local machine.
2. Open the project in your preferred Java IDE.
3. Build the project using your IDE's build tools.

Alternatively, you can use Gradle by adding the following code to your project:

## Client
```groovy
repositories {
      maven { url 'https://jitpack.io' }
}

dependencies {
      implementation 'com.github.FentusGames:CTU-Core:1.0'
      implementation 'com.github.FentusGames:CTU-Client:1.0'
}
```

## Server
```groovy
repositories {
      maven { url 'https://jitpack.io' }
}

dependencies {
      implementation 'com.github.FentusGames:CTU-Core:1.0'
      implementation 'com.github.FentusGames:CTU-Server:1.0'
}
```

## Usage

Check CTU-Client / CTU-Server for details on how to configure the client and/or server.

## Security

CTU-Core uses advanced encryption methods to ensure that all communications between clients and servers are secure. The application also includes robust error handling to ensure that any errors are caught and handled appropriately.