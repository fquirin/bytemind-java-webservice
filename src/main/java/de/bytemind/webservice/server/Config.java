package de.bytemind.webservice.server;

import java.util.Properties;

import de.bytemind.core.client.ClientDefaults;
import de.bytemind.core.client.Clients;
import de.bytemind.core.databases.DynamoDbConfig;
import de.bytemind.core.databases.ElasticSearchConfig;
import de.bytemind.core.databases.Elasticsearch;
import de.bytemind.core.tools.Debugger;
import de.bytemind.core.tools.FilesAndStreams;
import de.bytemind.core.tools.RandomGen;
import de.bytemind.core.users.Account;
import de.bytemind.core.users.IdHandler;
import de.bytemind.webservice.email.SendEmailBasicSmtp;
import de.bytemind.webservice.users.AccountDataDynamoDB;
import de.bytemind.webservice.users.AccountManagerDynamoDB;
import de.bytemind.webservice.users.GlobalIdGeneratorElasticsearch;

/**
 * Server configuration class.
 * 
 * @author Florian Quirin
 *
 */
public class Config {
	//helper for dynamic class creation
	//public static final String parentPackage = Config.class.getPackage().getName().substring(0, Config.class.getPackage().getName().lastIndexOf('.'));
			
	//test-settings
	public static boolean redirectEmail = false; 			//set this to true for email message testing
	
	//Basic configuration
	public static String configurationFolder = "Settings/";			//folder with external configuration files
	public static String configurationFile = configurationFolder + "config.properties";	//external configuration file
	public static String pluginsFolder = "Xtensions/";				//folder for plugins
	public static String webContentFolder = "WebContent/";			//folder with HTML files and static content
	public static String sslKeystore = "Settings/SSL/keystore.jks";		//path to SSL keystore
	public static String sslKeystorePwd = "storepassword";				//password for keystore
	public static String serverName = "bytemind-webservice-server";					//user defined local server name
	public static String sessionId = "ssid-" + RandomGen.randomInt(100000, 999999);	//random server-session- id generated at startup
	public static String serverSecret = "123456";				//user defined secret to validate local server
	public static int serverPort = 8001;						//server port
	public static boolean enableCORS = true;					//enable CORS (set access-control headers)
	public static boolean serveStaticFiles = false;				//enable static file serving
	public static boolean useSSL = false;						//enable SSL support
	public static boolean restrictRegistration = true; 		//check new registrations against white-list?
	
	//Api info and default URLs
	public static String api_version = "v0.8.0";				//API version
	//URLs - you can host files on this server or link to external sources
	public static String serverURL = "...assistant.link/"; 				//this API URL
	public static String createUserURL = "/#/login-define-password"; 	//URL to create user page
	public static String changePasswordURL = "/#/login-reset-password"; //URL to change password
	public static String webImagesURL = "...files.net/images/";			//Graphics than can be used in plug-ins
	public static String webFilesURL = "...files.net/";					//Files that can be linked
	public static String userDashboardURL = "Dashboard.html";			//User dashboard page
		
	//Default modules (implementations of certain interfaces)
	public static String accountData_module = AccountDataDynamoDB.class.getCanonicalName();
	public static String accountManager_module = AccountManagerDynamoDB.class.getCanonicalName(); //parentPackage + ".users.Authentication_DynamoDB";
	public static String authenticateFast_module = AccountManagerDynamoDB.class.getCanonicalName();
	//public static String authenticateFull_module = AccountManagerDynamoDB.class.getCanonicalName(); 	//moved to: ClientDefaults.authentication_module
	public static String knowledgeDB_module = Elasticsearch.class.getCanonicalName();
	public static String uidGenerator_module = GlobalIdGeneratorElasticsearch.class.getCanonicalName();
	public static String email_module = SendEmailBasicSmtp.class.getCanonicalName();
	
	//Default managers //TODO: security?
	public static final String sharedKey = "KantbyW3YLh8jTQPs5uzt2SzbmXZyphW"; 		//First step of inter-API communication security
	private static Account superuser;
	
	public static String superuserId = "uid1000";					//ID of superuser aka admin
	public static String superuserEmail = "12345@example.com";		//pseudo-email to be blocked for superuser, cannot be created or used for password reset
	private static String superuserPwdHash = "aa22d38a3ba9ca8712364140a1166c9b80a296ee24dfb4e503fe728a8ed4a222";
	public static boolean validateSuperuser(){
		superuser = new Account(superuserId + ";" + superuserPwdHash, IdHandler.Type.uid, Clients.API_SERVER);
		return superuser.authenticate();
	}
	
	//Email - default SMTP with authentication (port 25)
	public static String mailHost = "smtp...com";
	public static String emailAccount = "account@...com";
	public static String emailAccountKey = "";
	public static String emailDebugBCC = "";

	
	//----------helpers----------
	
	/**
	 * Load server settings from default properties file. 
	 */
	public static Properties load_settings(){
		return load_settings(configurationFile);
	}
	/**
	 * Load server settings from properties file. 
	 */
	public static Properties load_settings(String configFile){
		if (configFile == null || configFile.isEmpty())	configFile = configurationFile;
		
		try{
			Properties settings = FilesAndStreams.loadSettings(configFile);
			//server
			serverURL = settings.getProperty("server_endpoint_url");	
			serverName = settings.getProperty("server_local_name");
			serverSecret = settings.getProperty("server_local_secret");
			serverPort = Integer.valueOf(settings.getProperty("server_port"));
			//databases
			DynamoDbConfig.setRegion(settings.getProperty("db_dynamo_region", ""));
			DynamoDbConfig.setAccess(settings.getProperty("amazon_dynamoDB_access"));
			DynamoDbConfig.setSecret(settings.getProperty("amazon_dynamoDB_secret"));
			ElasticSearchConfig.setEndpoint(settings.getProperty("db_elastic_endpoint", ""));
			//web content
			createUserURL = settings.getProperty("url_createUser"); 	
			changePasswordURL = settings.getProperty("url_changePassword"); 
			webImagesURL = settings.getProperty("url_web_images"); 	
			webFilesURL = settings.getProperty("url_web_files"); 		
			userDashboardURL = settings.getProperty("url_dashboard"); 		
			//email account
			mailHost = settings.getProperty("email_host");
			emailAccount = settings.getProperty("email_account");
			emailAccountKey = settings.getProperty("email_account_key");
			emailDebugBCC = settings.getProperty("email_bcc", "");
			//credentials
			IdHandler.user_id_prefix = settings.getProperty("user_id_prefix");
			superuserId = settings.getProperty("superuser_id");
			superuserEmail = settings.getProperty("superuser_email");
			superuserPwdHash = settings.getProperty("superuser_pwd_hashed");
			//modules
			authenticateFast_module = settings.getProperty("authenticateFast_module");
			ClientDefaults.authentication_module = settings.getProperty("authenticateFull_module");
			ClientDefaults.auth_endpoint_url = settings.getProperty("authentication_url");
			accountData_module = settings.getProperty("accountData_module");
			accountManager_module = settings.getProperty("accountManager_module");
			knowledgeDB_module = settings.getProperty("knowledgeDB_module");
			uidGenerator_module = settings.getProperty("uidGenerator_module");
			email_module = settings.getProperty("email_module");
			
			Debugger.println("loading settings from " + configFile + "... done." , 3);
			return settings;
		
		}catch (Exception e){
			Debugger.println("loading settings from " + configFile + "... failed!" , 1);
			return null;
		}
	}
	/**
	 * Recreate server settings file (credentials will be empty);
	 */
	public static void save_settings(String configFile){
		if (configFile == null || configFile.isEmpty())	configFile = configurationFile;
		
		//save all personal parameters
		Properties config = new Properties();
		//server
		config.setProperty("server_url", serverURL);
		config.setProperty("server_name", serverName);
		config.setProperty("server_secret", serverSecret);
		config.setProperty("server_port", String.valueOf(serverPort));
		//databases
		config.setProperty("db_dynamo_region", DynamoDbConfig.getRegion());
		config.setProperty("amazon_dynamoDB_access", "");
		config.setProperty("amazon_dynamoDB_secret", "");
		config.setProperty("db_elastic_endpoint", ElasticSearchConfig.getEndpoint());
		//web content
		config.setProperty("url_createUser", createUserURL); 	
		config.setProperty("url_changePassword", changePasswordURL); 
		config.setProperty("url_web_images", webImagesURL); 	
		config.setProperty("url_web_files", webFilesURL); 		
		config.setProperty("url_dashboard", userDashboardURL); 		
		//email account
		config.setProperty("email_host", mailHost);
		config.setProperty("email_account", emailAccount);
		config.setProperty("email_account_key", "");
		config.setProperty("email_bcc", emailDebugBCC);
		//credentials
		config.setProperty("user_id_prefix", IdHandler.user_id_prefix);
		config.setProperty("superuser_id", superuserId);
		config.setProperty("superuser_email", "");
		config.setProperty("superuser_pwd_hashed", "");
		//modules
		config.setProperty("authenticateFast_module", authenticateFast_module);
		config.setProperty("authenticateFull_module", ClientDefaults.authentication_module);
		config.setProperty("authenticationEndpoint_url", ClientDefaults.auth_endpoint_url);
		config.setProperty("accountData_module", accountData_module);
		config.setProperty("accountManager_module", accountManager_module);
		config.setProperty("knowledgeDB_module", knowledgeDB_module);
		config.setProperty("uidGenerator_module", uidGenerator_module);
		config.setProperty("email_module", email_module);
		
		try{
			FilesAndStreams.saveSettings(configFile, config);
			Debugger.println("saving settings to " + configFile + "... done." , 3);
		}catch (Exception e){
			Debugger.println("saving settings to " + configFile + "... failed!" , 1);
		}
	}

}
