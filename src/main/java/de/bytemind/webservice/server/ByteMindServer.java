package de.bytemind.webservice.server;

/**
 * The ByteMind server is a modular server for webservices and websites. You can build your individual version or own API on top of 
 * several basic servers that include functionalities for account management or plugins to support all sorts of communication platforms
 * like Amazon Echo, Facebook messenger etc..
 * 
 * @author Florian Quirin
 */
public abstract class ByteMindServer {
	
	public static final String LIVE_SERVER = "live";
	public static final String TEST_SERVER = "test";
	public static final String CUSTOM_SERVER = "custom";
	
	public static final String ACCOUNT_ATTR = "account";	//attribute to get account, in case you buffer it in "before"
	
	/**
	 * Load server (and usually database) settings. 
	 */
	public abstract void loadSettings(String[] args);
	
	/**
	 * Use this to setup the database(s) used by the server. Load server settings before!
	 * Usually only called at first server start as this might overwrite database entries if they already exist.
	 */
	public abstract void createDatabases();
	
	/**
	 * Create the server-admin account via interactive console input. Returns GUUID of admin when successful or empty.
	 */
	public abstract String createAdminAccount();
	
	/**
	 * Create the an account via user via interactive console input. Returns GUUID of user when successful or empty.
	 */
	public abstract String createNewUserLocally();
	
	/**
	 * Main method to start server. This method also loads the global server and database configuration (if not already loaded before).
	 */
	public abstract void start(String[] args);

}
