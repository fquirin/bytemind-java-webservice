package de.bytemind.webservice.server;

import static spark.Spark.*;

import java.security.Policy;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import org.json.simple.JSONObject;

import de.bytemind.core.server.Statistics;
import de.bytemind.core.tools.ClassBuilder;
import de.bytemind.core.tools.DateTime;
import de.bytemind.core.tools.Debugger;
import de.bytemind.core.tools.Is;
import de.bytemind.core.tools.JSON;
import de.bytemind.core.tools.SandboxSecurityPolicy;
import de.bytemind.core.tools.Timer;
import de.bytemind.core.users.Account;
import de.bytemind.core.users.Role;
import de.bytemind.webservice.email.SendEmail;
import de.bytemind.webservice.users.AccountManager;
import de.bytemind.webservice.users.AuthenticationToken;
import spark.Request;
import spark.Response;

/**
 * Basic server typically using external or instant authentication of accounts (if required at all) and no database.<br>
 * Best used for dynamic content that requires no accounts and no databases (simple APIs or static/partially dynamic websites).
 *    
 * @author Florian Quirin
 *
 */
public class BasicServer extends ByteMindServer{
	
	//stuff
	public String startUTC = "";
	public String serverType = "";
	private boolean useSSL = false;
	private boolean serveStaticFiles = false;
	
	private boolean settingsLoaded = false;
	
	/**
	 * Setup sandbox to secure access of plugins loaded via SandboxClassLoader. 
	 */
	public void loadSandbox(){
		Policy.setPolicy(new SandboxSecurityPolicy());
		System.setSecurityManager(new SecurityManager());
	}
	/**
	 * Load SSL certificates from keystore. If you want to use SSL you might need to overwrite it unless keystore + password works for you.<br>
	 * Note: usually a reverse-proxy like NGINX is better suited to handle SSL and its also able to auto-load SSL certificates from Letsencrypt ;-)
	 */
	public void loadSSL(){
		secure(Config.sslKeystore, Config.sslKeystorePwd, null, null);
	}
	
	/**
	 * Serve static files at "Config.webContentFolder".
	 */
	public void serveStaticFiles(){
		staticFiles.externalLocation(Config.webContentFolder);
	}
	
	/**
	 * Check arguments and load proper settings file.
	 * @param args - parameters submitted to main method
	 */
	public void loadSettings(String[] args){
		//check arguments
		serverType = LIVE_SERVER;
		for (String arg : args){
			if (arg.equals("--test")){
				//Test system
				serverType = TEST_SERVER;
			}else if (arg.equals("--my") || arg.equals("--custom")){
				//Custom system
				serverType = CUSTOM_SERVER;
			}else if (arg.equals("--ssl")){
				//SSL
				useSSL = true;
			}else if (arg.equals("--files")){
				//Serve static content
				serveStaticFiles = true;
			}
		}
		if (serverType.equals(TEST_SERVER)){
			Debugger.println("-- THIS IS A TEST SYSTEM --", 3);
			Config.configurationFile = Config.configurationFolder + "config.test.properties";
			Config.load_settings(Config.configurationFile);
		}else if (serverType.equals(CUSTOM_SERVER)){
			Debugger.println("-- THIS IS A CUSTOM SYSTEM --", 3);
			Config.configurationFile = Config.configurationFolder + "config.custom.properties";
			Config.load_settings(Config.configurationFile);
		}else{
			Debugger.println("-- THIS IS A LIVE SYSTEM --", 3);
			Config.load_settings("");
		}
		if (Config.serveStaticFiles || serveStaticFiles){
			serveStaticFiles();
			Debugger.println("Serving static files from: " + Config.webContentFolder, 3);
		}
		if (Config.useSSL || useSSL){
			loadSSL();
			Debugger.println("Loading SSL keystore: " + Config.sslKeystore, 3);
		}
		settingsLoaded = true;
	}
	
	/**
	 * Methods to test modules before sever start.
	 */
	public boolean testModules(){
		//TODO: ADD MORE BASIC MODULE-TESTS?
		return true;
	}
	
	/**
	 * All kinds of things that should be loaded on startup. You can overwrite this method to add your own stuff.
	 */
	public void setupStuff(){
		//add stuff here that needs to be set up at server start 
	}
	
	/**
	 * Sets the default end-points: ping, stats and config.
	 */
	public void loadDefaultEndpoints(){
		get("/ping", (request, response) -> 			pingServer(request, response));
		get("/stats", (request, response) -> 			serverStats(request, response));
		get("/config", (request, response) -> 			configServer(request, response));
	}
	
	/**
	 * Handle unexpected errors 
	 */
	public void handleError(){
		exception(Exception.class, (ex, request, response) -> {
			Debugger.println("Exception for request to " + request.url() + ": " + ex, 1);
			//print the last 5 traces ... the rest is typically server spam ^^
			Debugger.printStackTrace(ex, 5);
			JSONObject result = new JSONObject();
			JSON.add(result, "result", "fail");
			JSON.add(result, "error", "500 internal error");
			JSON.add(result, "info", ex.getMessage());
			response.body(returnResult(request, response, result.toJSONString(), 200)); 	//code 500 always creates client timeout :/
		});
	}
	
	@Override
	public void createDatabases() {
		//Basic server has no database requirements.
	}
	
	@Override
	public String createAdminAccount(){
		AccountManager accountManager = (AccountManager) ClassBuilder.construct(Config.accountManager_module);
		String adminGuuid = "";
		if (accountManager.testModule()){
			adminGuuid = accountManager.createAdminAccount();
		}else{
			throw new RuntimeException("The account-manager module required to create the admin account does NOT work properly! Please check your server config-file and database connections.");
		}
		return adminGuuid;
	}
	
	@Override
	public String createNewUserLocally(){
		AccountManager accountManager = (AccountManager) ClassBuilder.construct(Config.accountManager_module);
		String userGuuid = "";
		if (accountManager.testModule()){
			userGuuid = accountManager.createNewUserLocally();
		}else{
			throw new RuntimeException("The account-manager module required to create the account does NOT work properly! Please check your server config-file and database connections.");
		}
		return userGuuid;
	}
	
	@Override
	public void start(String[] args) {
		
		//load settings (and setup core tools)
		if (!settingsLoaded){
			loadSettings(args);
		}
		
		//load sandbox
		loadSandbox();
				
		//get UTC date
		Date date = new Date();
		startUTC = DateTime.getUTC(date, "dd.MM.yyyy' - 'HH:mm:ss' - UTC'");
		Debugger.println("Starting server " + Config.api_version + " (" + serverType + ")", 3);
		Debugger.println("date: " + startUTC, 3);
		
		//email warnings?
		if (!Config.emailDebugBCC.isEmpty()){
			Debugger.println("WARNING: Emails are sent to " + Config.emailDebugBCC + " for debugging issues!", 3);
		}
		
		//test modules
		if (!testModules()){
			throw new RuntimeException("Problems with server-modules, cannot proceed! Are all databases working?");
		}else{
			Debugger.println("Server-modules (database connections) seem to work fine.", 3);
		}
		
		//load statics, modules and workers
		if (!Config.validateSuperuser()){
			Debugger.println("Superuser token not valid!", 1);
			throw new RuntimeException("Problems with admin-account, cannot proceed! Did you already create the account? Are all databases working?");
		}else{
			Debugger.println("Superuser token validated.", 3);
		}
				
		//setup modules like loading stuff to memory and connecting commands to classes etc.
		setupStuff();
		
		/*
		//TODO: do we need to set this? https://wiki.eclipse.org/Jetty/Howto/High_Load
		int maxThreads = 8;
		int minThreads = 2;
		int timeOutMillis = 30000;
		threadPool(maxThreads, minThreads, timeOutMillis)
		 */
		
		try {
			port(Integer.valueOf(System.getenv("PORT")));
			Debugger.println("server running on port: " + Integer.valueOf(System.getenv("PORT")), 3);
		}catch (Exception e){
			int port = Config.serverPort;
			port(port);
			Debugger.println("server running on port "+ port, 3);
		}

		//set access-control headers to enable CORS
		before((request, response) -> {
			if (Config.enableCORS){
				enableCORS(response, "*", "*", "*");
			}
		});
		
		//ERROR handling - TODO: improve
		handleError();
		
		//SERVER END-POINTS
		loadDefaultEndpoints();
	}
	
	/**
	 * ---PING SERVER---<br>
	 * End-point to ping the server and get back some basic info like API version.
	 */
	public String pingServer(Request request, Response response){
		//stats
		Statistics.addInternalApiHit("Endpoint:ping", 1);
					
		JSONObject msg = new JSONObject();
		JSON.put(msg, "result", "success");
		JSON.put(msg, "name", Config.serverName);
		JSON.put(msg, "version", "API " + Config.api_version);

		return returnResult(request, response, msg.toJSONString(), 200);
	}
		
	/**
	 * ---SERVER STATS---<br>
	 * End-point to get statistics of the server.
	 */
	public String serverStats(Request request, Response response){
		long tic = Timer.tic();
		
		//authenticate
		Account account = authenticateAccount(request);
		if (account == null || !account.authenticate()){
			Statistics.addInternalApiHit("Endpoint:serverStats-noAuth", tic);	//Statistics
			return returnNoAccess(request, response, (account == null)? 2 : account.getErrorCode());
		
		}else{
			response.status(200);
			response.type("text/plain; charset=utf-8");
			
			Date date = new Date();
			String nowUTC = DateTime.getUTC(date, "dd.MM.yyyy' - 'HH:mm:ss' - UTC'");
			String nowLocal = DateTime.getToday("dd.MM.yyyy' - 'HH:mm:ss' - LOCAL'", request.queryParams("time_local"));
			
			//get user role
			if (account.hasRole(Role.seniordev)){
				//msg
				String msg = "Hello World!\n\nStats:\n" +
								"\nServer name: " + Config.serverName +
								"\nAPI version: " + Config.api_version +
								"\nSession ID: " + Config.sessionId +
								"\nServer started: " + startUTC +
								"\nTime now: " + nowUTC + 
								"\nTime local: " + nowLocal + "\n\n" +
								Statistics.getInfoAsString() +
								"\n" +
								serverStatsHook(request, response, account);
				
				Statistics.addInternalApiHit("Endpoint:serverStats", tic);			//Statistics
				return msg;
				
			}else{
				Statistics.addInternalApiHit("Endpoint:serverStats-error", tic);	//Statistics
				return ("Please login with role '" + Role.seniordev + "' or higher to see statistics.");
			}
		}
	}
	/**
	 * Hook into server statistics end-point and append custom data.
	 * @return string to append to statistics overview
	 */
	public String serverStatsHook(Request request, Response response, Account account){
		return "-fin-";
	}
	
	/**
	 * ---CONFIG SERVER---<br>
	 * End-point to remotely switch certain settings on run-time.
	 */
	public String configServer(Request request, Response response){
		long tic = Timer.tic();
		
		//authenticate
		Account account = authenticateAccount(request);
		if (account == null || !account.authenticate()){
			Statistics.addInternalApiHit("Endpoint:configServer-noAuth", tic);		//Statistics
			return returnNoAccess(request, response, (account == null)? 2 : account.getErrorCode());
		
		}else{
			//check role
			if (!account.hasRole(Role.superuser)){
				Debugger.println("unauthorized access attempt to server config! User: " + account.getUserID(), 3);
				Statistics.addInternalApiHit("Endpoint:configServer-noRole", tic);		//Statistics
				return returnNoAccess(request, response);
			}
			
			//check actions
			
			//-email bcc
			String emailBCC = request.queryParams("emailBCC");
			if (emailBCC != null && (emailBCC.equals("") || emailBCC.equals("remove"))){
				Config.emailDebugBCC = "";
			}
			//-email reload templates
			String emailTemplates = request.queryParams("emailTemplates");
			if (emailTemplates != null && (emailTemplates.equals("") || emailTemplates.equals("reload"))){
				SendEmail emailClient = (SendEmail) ClassBuilder.construct(Config.email_module);
				emailClient.refreshTemplates();
			}
			
			JSONObject msg = new JSONObject();
			JSONObject data = new JSONObject();
			JSON.add(msg, "configuration", data);
			JSON.add(msg, "serverSessionId", Config.sessionId);
			JSON.add(data, "emailBCC", Config.emailDebugBCC);
			Map<String, Object> customStuff = configServerHook(request, response);
			if (customStuff != null){
				for (Entry<String, Object> es : customStuff.entrySet()){
					JSON.add(data, es.getKey(), es.getValue());
				}
			}
			Statistics.addInternalApiHit("Endpoint:configServer", tic);		//Statistics
			return returnResult(request, response, msg.toJSONString(), 200);
		}
	}
	/**
	 * Hook into configuration end-point and add custom data.
	 * @return Map with custom key-value pairs to be added to result
	 */
	public Map<String, Object> configServerHook(Request request, Response response){
		//Example:
		/*
		Map<String, Object> myConfig = new HashMap<>();
		String myConfigParameter = request.queryParams("myConfigParameter");
		if (myConfigParameter != null){
			//Do action
			//...
			//Add to info
			myConfig.put("myConfigParameter", 12345);
		}
		return myConfig;
		*/
		return null;
	}
	
	//------- return methods and header manipulation -------
	
	/**
	 * Return result as requested content (plain-text, javascript, json, etc...)
	 * @param request - request containing the header (content-type)
	 * @param response - expected response (passed down from main)
	 * @param msg - message to return (text, json string, ...)
	 * @param statusCode - HTTP result code (200,401,...)
	 * return proper result string
	 */
	public String returnResult(Request request, Response response, String msg, int statusCode){
		//get content header
		String header = request.headers("Content-type");
		//System.out.println("Content-type: " + header);
		if (header == null){
			header = request.headers("Accept");
			if (header == null){
				header = "";
			}
		}
		//System.out.println(header);		//debug
		//System.out.println(request.queryString().length());		//content length
		
		//return answer in requested format
		response.status(statusCode);
		if (header.contains("application/javascript")){
			response.type("application/javascript");
			msg = request.queryParams("callback") + "(" + msg + ");";
		}else if (header.contains("text/")){
			if (msg.startsWith("{") || msg.startsWith("[")){
				response.type("text/plain; charset=utf-8");
			}else{
				response.type("text/html; charset=utf-8");
			}
		}else if (header.matches(".*/json($|;| ).*")){
			response.type("application/json");
		}else if (header.contains("multipart/form-data")){
			if (msg.startsWith("{") || msg.startsWith("[")){
				response.type("text/plain; charset=utf-8");
			}else{
				response.type("text/html; charset=utf-8");
			}
		}else{
			response.type("application/javascript");
			msg = request.queryParams("callback") + "(" + msg + ");";
		}
		return msg;
	}
	public String returnNoAccess(Request request, Response response){
		/*
		response.status(401);
		response.type("text/plain");
		return "401 Unauthorized";
		*/
		String msg = "{\"result\":\"fail\",\"error\":\"401 not authorized\"}";
		return returnResult(request, response, msg, 401);
	}
	public String returnNoAccess(Request request, Response response, int errorCode){
		if (errorCode == 2){
			return returnNoAccess(request, response);
		}else{
			String msg = "{\"result\":\"fail\",\"error\":\"400 or 500 bad request or communication error\",\"code\":\"" + errorCode + "\"}";
			return returnResult(request, response, msg, 500);
		}
	}
	
	/**
	 * Authenticate the user via (presumably) faster token.
	 * @param request - the request (aka URL-parameters) sent to server.
	 */
	protected AuthenticationToken authenticateToken(Request request){
		Credentials credentials = new Credentials(request);
		if (Is.notNullOrEmpty(credentials.idType)){
			return null;
		}else{
			return new AuthenticationToken(credentials.username, credentials.password, 
						credentials.idType, credentials.client, request);
		}
	}
	/**
	 * Authenticate the user via (presumably) slower account class, filled with all the default info about the user.
	 * @param request - the request (aka URL-parameters) sent to server.
	 */
	protected Account authenticateAccount(Request request){
		Credentials credentials = new Credentials(request);
		if (Is.notNullOrEmpty(credentials.idType)){
			return null;
		}else{
			return new Account(credentials.key, credentials.idType, credentials.client);
		}
	}
	
	/**
	 * Enable CORS aka set access-control headers.
	 */
	public static void enableCORS(Response response, final String origin, final String methods, final String headers) {
	   response.header("Access-Control-Allow-Origin", origin);
	   response.header("Access-Control-Request-Method", methods);
	   response.header("Access-Control-Allow-Headers", headers);
	}

}
