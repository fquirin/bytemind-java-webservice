package de.bytemind.webservice.server;

public class Start {

	//MAIN
	public static void main(String[] args) {
		
		//Choose a server
		ByteMindServer server = new BasicAuthenticationServer();
		
		//Do some custom settings before loading the rest from file (note: file-settings overwrite custom)
		Config.api_version = "0.0.1";
		
		//Load server settings from file (and arguments)
		server.loadSettings(args);
		
		//And GO
		server.start(args);
	}

}
