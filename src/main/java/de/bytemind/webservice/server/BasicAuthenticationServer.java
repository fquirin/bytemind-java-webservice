package de.bytemind.webservice.server;

import static spark.Spark.*;

import org.json.simple.JSONObject;

import de.bytemind.core.server.Statistics;
import de.bytemind.core.statics.Language;
import de.bytemind.core.tools.ClassBuilder;
import de.bytemind.core.tools.Debugger;
import de.bytemind.core.tools.Is;
import de.bytemind.core.tools.JSON;
import de.bytemind.core.tools.Timer;
import de.bytemind.core.users.Account;
import de.bytemind.core.users.AccountBasicInfo;
import de.bytemind.core.users.Role;
import de.bytemind.webservice.database.DB;
import de.bytemind.webservice.database.SetupDynamoDB;
import de.bytemind.webservice.database.SetupElasticsearch;
import de.bytemind.webservice.email.SendEmail;
import de.bytemind.webservice.users.AccountManager;
import de.bytemind.webservice.users.AuthenticationToken;
import spark.Request;
import spark.Response;

/**
 * Basic server with user account management and data storage using a combination of DynamoDB and Elasticsearch databases.
 *  
 * @author Florian Quirin
 *
 */
public class BasicAuthenticationServer extends BasicServer{
	
	@Override
	public void setupStuff(){
		super.setupStuff();
	}
	
	@Override
	public boolean testModules(){
		super.testModules();
		
		boolean accountData = DB.getAccountsDB().testModule();
		boolean knowledgeDB = DB.getKnowledgeDB().testConnection();
		//TODO: ADD MORE MODULE-TESTS?
		return (accountData && knowledgeDB);
	}
	
	@Override
	public void createDatabases(){
		super.createDatabases();

		//Ticket storage for DynamoDB
		SetupDynamoDB.setupBasicsTables();
		//User accounts storage for DynamoDB
		SetupDynamoDB.setupAccountsTables();
		
		//Mappings for Elasticsearch
		SetupElasticsearch.setupGuidGenerationMapping();
		SetupElasticsearch.setupAuthenticationWhitelistMapping();
	}
	
	/**
	 * Sets the authentication end-points so that this server can be used to create and authenticate users.
	 */
	public void loadAuthenticationEndpoints(){
		post("/authentication", (request, response) -> 	authenticationAPI(request, response));
		post("/authWhitelist", (request, response) ->	authenticationWhitelist(request, response));
	}
	
	@Override
	public void start(String[] args) {
		super.start(args);
		
		//ADD SERVER END-POINTS
		loadAuthenticationEndpoints();
	}
	
	/**
	 * ---Authentication whitelist API---<br>
	 * End-point to manage whitelist for new user registration.
	 */
	public String authenticationWhitelist(Request request, Response response){
		long tic = Timer.tic();
		
		//authenticate
		Account account = authenticateAccount(request);
		if (account == null || !account.authenticate()){
			Statistics.addInternalApiHit("Endpoint:authWhitelist-noAuth", tic);		//Statistics
			return returnNoAccess(request, response, (account == null)? 2 : account.getErrorCode());
		
		}else{
			//get service
			String action = request.queryParams("action");
			String info = request.queryParams("info");
			
			//validate request
			if (action == null || info == null){
				String msg = "{\"result\":\"fail\",\"error\":\"parameters are missing or invalid!\"}";
				Statistics.addInternalApiHit("Endpoint:authWhitelist-error", tic);		//Statistics
				return returnResult(request, response, msg, 200);
			}
			
			//check role
			if (!account.hasRole(Role.developer)){
				Debugger.println("access denied to service whitelist! User: " + account.getUserID() + " is missing role.", 3);
				Statistics.addInternalApiHit("Endpoint:authWhitelist-noRole", tic);		//Statistics
				return returnNoAccess(request, response);
			}
			
			//add
			if (action.equals("addUser")){
				//add user to list
				int code = DB.saveWhitelistUserEmail(info);
				//success?
				if (code != 0){
					String msg = "{\"result\":\"fail\",\"error\":\"user could not be added!\",\"code\":\"" + code + "\"}";
					Statistics.addInternalApiHit("Endpoint:authWhitelist:addUser-error", tic);		//Statistics
					return returnResult(request, response, msg, 200);
				}else{
					Debugger.println("Whitelist user added! User: " + info + " added by: " + account.getUserID(), 3);
					JSONObject msg = new JSONObject();
					JSON.add(msg, "result", "success");
					JSON.add(msg, "added", info);
					JSON.add(msg, "by", account.getUserID());
					Statistics.addInternalApiHit("Endpoint:authWhitelist:addUser", tic);			//Statistics
					return returnResult(request, response, msg.toJSONString(), 200);
				}
			
			//no valid service
			}else{
				String msg = "{\"result\":\"fail\",\"error\":\"parameters are invalid!\"}";
				Statistics.addInternalApiHit("Endpoint:authWhitelist-error", tic);		//Statistics
				return returnResult(request, response, msg, 200);
			}
		}
	}
	
	/**
	 * ---AUTHENTICATION API---<br>
	 * End-point that handles the user creation, authentication process, login, logout, password reset, etc.<br>
	 * Returns e.g. a JSON object with login-token or registration link.
	 */
	public String authenticationAPI(Request request, Response response) {
		long tic = Timer.tic();
		
		//get action - validate/logout/createUser/deleteUser
		String action = request.queryParams("action");
		String client_info = request.queryParams("client");
		
		//no action
		if (action == null || action.trim().isEmpty()){
			Statistics.addInternalApiHit("Endpoint:authentication:null", tic);		//Statistics
			return returnResult(request, response, "no action", 204);
		}
		//check user - this is mainly a service for other APIs - basically same as validate but without token generation
		else if (action.trim().equals("check")){
			//authenticate
			AuthenticationToken token = authenticateToken(request);
			if (!token.authenticated()){
				Statistics.addInternalApiHit("Endpoint:authentication:check-noAuth", tic);		//Statistics
				return returnNoAccess(request, response, token.getErrorCode());
				
			}else{
				//success
				JSONObject msg = new JSONObject();
				JSON.add(msg, "result", "success");
				JSON.add(msg, "access_level", token.getAccessLevel());
				//id
				String guuid = token.getUserID();
				JSON.add(msg, "uid", guuid);
				//basic info
				//HashMap<String, Object> basics = token.getRawBasicInfo();
				AccountBasicInfo basics = token.upgradeAndGetBasicInfo();
				if (basics != null){
					JSON.add(msg, "basic_info", basics);
				}
				JSON.add(msg, "duration_ms", Debugger.toc(tic));
				Statistics.addInternalApiHit("Endpoint:authentication:check", tic);		//Statistics
				return returnResult(request, response, msg.toJSONString(), 200);
			}
		}
		//validate user and create new token
		else if (action.trim().equals("validate")){
			//authenticate
			AuthenticationToken token = authenticateToken(request);
			if (!token.authenticated()){
				Statistics.addInternalApiHit("Endpoint:authentication:validate-noAuth", tic);		//Statistics
				return returnNoAccess(request, response, token.getErrorCode());
				
			}else{
				//success: make a new token and save it in the database
				long timeStamp = System.currentTimeMillis();
				String new_token = token.getKeyToken(client_info);
				if (token.getErrorCode() != 0 && !new_token.isEmpty()){
					String msg = "{\"result\":\"fail\",\"error\":\"cannot create token, maybe invalid client info?\"}";
					Statistics.addInternalApiHit("Endpoint:authentication:validate-error", tic);		//Statistics
					return returnResult(request, response, msg, 200);
				}
				JSONObject msg = new JSONObject();
				JSON.add(msg, "result", "success");
				JSON.add(msg, "access_level", token.getAccessLevel());
				JSON.add(msg, "keyToken", new_token);
				JSON.add(msg, "keyToken_TS", new Long(timeStamp));
				JSON.add(msg, "duration_ms", Debugger.toc(tic));
				//id
				String guuid = token.getUserID();
				JSON.add(msg, "uid", guuid);
				//basic info
				//HashMap<String, Object> basics = token.getRawBasicInfo();
				AccountBasicInfo basics = token.upgradeAndGetBasicInfo();
				if (basics != null){
					JSON.add(msg, "basic_info", basics);
				}
				Statistics.addInternalApiHit("Endpoint:authentication:validate", tic);		//Statistics
				return returnResult(request, response, msg.toJSONString(), 200);
			}
		}
		//logout
		else if (action.trim().equals("logout")){
			//authenticate
			AuthenticationToken token = authenticateToken(request);
			if (!token.authenticated()){
				Statistics.addInternalApiHit("Endpoint:authentication:logout-noAuth", tic);		//Statistics
				return returnNoAccess(request, response, token.getErrorCode());
				
			}else{
				//success: logout user
				boolean success = token.logoutUser(client_info);
				JSONObject msg = new JSONObject();
				if (success){
					JSON.add(msg, "result", "success");
					JSON.add(msg, "msg", "user successfully logged out.");
				}else{
					JSON.add(msg, "result", "fail");
					JSON.add(msg, "error", "user logout failed!");
					JSON.add(msg, "code", token.getErrorCode());
				}
				Statistics.addInternalApiHit("Endpoint:authentication:logout", tic);		//Statistics
				return returnResult(request, response, msg.toJSONString(), 200);
			}
		}
		
		//register new user
		else if (action.trim().equals("register")){
			
			String userID = request.queryParams("userid");	//any of the allowed unique IDs, e.g. email address
			String type = request.queryParams("type");		//type of registration, e.g. "email"
			String language = request.queryParams("lang");	//language for email
			if (language == null){
				language = Language.DE.toValue();
			}
			
			//check type
			if (type == null || !type.equals("email")){
				String msg = "{\"result\":\"fail\",\"error\":\"'type' of registration not supported!\"}";
				Statistics.addInternalApiHit("Endpoint:authentication:register-error", tic);		//Statistics
				return returnResult(request, response, msg, 200);
			}
			
			//V1: Email registration:
			
			//user id must be at least 4 chars, better: email
			if (userID != null && userID.length() > 4 && userID.contains("@")){
				String email = userID;
				//-log
				Debugger.println("registration attempt - ID: " + email + " - timestamp: " + System.currentTimeMillis(), 3);
				
				AccountManager auth = (AccountManager) ClassBuilder.construct(Config.accountManager_module);
				auth.setRequestInfo(request);
				JSONObject result = auth.registrationByEmail(email);
				//check result for user-exists or server communication error
				if (((String) result.get("result")).equals("fail")){
					Statistics.addInternalApiHit("Endpoint:authentication:register-error", tic);		//Statistics
					return returnResult(request, response, result.toJSONString(), 200);
				}
				//Send via email:
				//-create message
				SendEmail emailClient = (SendEmail) ClassBuilder.construct(Config.email_module);
				
				String subject = "Please confirm your e-mail address and off we go";
				if (language.equals(Language.DE.toValue())){
					subject = "Bitte bestätige deine E-Mail Adresse und los geht's";
				}
				
				String message = emailClient.loadDefaultRegistrationMessage(language, email,
									(String) result.get("ticketid"),
									(String) result.get("token"),
									(String) result.get("time")
				);
				//-send
				int code = emailClient.send(email, message, subject, null);

				//-check result
				if (code == 0){
					//-overwrite token and return
					JSON.add(result, "token", "sent via email to " + email);
					Statistics.addInternalApiHit("Endpoint:authentication:register", tic);		//Statistics
					return returnResult(request, response, result.toJSONString(), 200);
				}else{
					//-error
					Statistics.addInternalApiHit("Endpoint:authentication:register-emailError", tic);		//Statistics
					if (code == 1){
						return returnResult(request, response, "{\"result\":\"fail\",\"error\":\"could not send email! Invalid address? Server problem?\",\"code\":\"418\"}", 200);
					}else if (code == 2){
						return returnResult(request, response, "{\"result\":\"fail\",\"error\":\"could not send email! Invalid address?\",\"code\":\"422\"}", 200);
					}else{
						return returnResult(request, response, "{\"result\":\"fail\",\"error\":\"could not send email! Server problem?\",\"code\":\"500\"}", 200);
					}
				}
			}
			Statistics.addInternalApiHit("Endpoint:authentication:register-error", tic);		//Statistics
			return returnResult(request, response, "{\"result\":\"fail\",\"error\":\"no valid user ID found!\",\"code\":\"422\"}", 200);
		}
		//create new user
		else if (action.trim().equals("createUser")){
			String userID = request.queryParams("userid");
			String password = request.queryParams("pwd");
			String token = request.queryParams("token");
			String type = request.queryParams("type");
			String timestamp = request.queryParams("time");
			String ticketID = request.queryParams("ticketid");
			String language = request.queryParams("lang");
			if (userID != null && ticketID != null && password != null && token != null && timestamp != null && type != null){
				AccountManager auth = (AccountManager) ClassBuilder.construct(Config.accountManager_module);
				auth.setRequestInfo(request);
				JSONObject in = new JSONObject();
				JSON.add(in, "userid", userID.trim());		JSON.add(in, "pwd", password.trim());
				JSON.add(in, "token", token.trim());		JSON.add(in, "type", type);	
				JSON.add(in, "time", timestamp);			JSON.add(in, "ticketid", ticketID);
				if (Is.notNullOrEmpty(language))	JSON.add(in, "language", language);
				boolean success = auth.createUser(in);
				if (success){
					String msg = "{\"result\":\"success\",\"msg\":\"new user created\"}";
					//-log
					Debugger.println("new user created - ID: " + userID + " - timestamp: " + System.currentTimeMillis(), 3);
					Statistics.addInternalApiHit("Endpoint:authentication:createUser", tic);		//Statistics
					return returnResult(request, response, msg, 200);
				}else{
					String msg = "{\"result\":\"fail\",\"error\":\"failed to create user!\",\"code\":\"" + auth.getErrorCode() + "\"}";
					Statistics.addInternalApiHit("Endpoint:authentication:createUser-error", tic);		//Statistics
					return returnResult(request, response, msg, 200);
				}
			}else{
				String msg = "{\"result\":\"fail\",\"error\":\"401 not authorized to create user (wrong token? missing parameters?)!\",\"code\":\"" + "2" + "\"}";
				Statistics.addInternalApiHit("Endpoint:authentication:createUser-tokenError", tic);		//Statistics
				return returnResult(request, response, msg, 200);
			}
		}
		
		//request password change
		else if (action.trim().equals("forgotPassword")){
			
			String userID = request.queryParams("userid");	//any of the allowed unique IDs, e.g. email address
			String type = request.queryParams("type");		//type to use for recovery, e.g. "email"
			String language = request.queryParams("lang");	//language for email
			if (language == null){
				language = Language.DE.toValue();
			}
			
			//check type
			if (type == null || !type.equals("email")){
				String msg = "{\"result\":\"fail\",\"error\":\"this 'type' to reset password is not supported!\"}";
				Statistics.addInternalApiHit("Endpoint:authentication:forgotPassword-error", tic);		//Statistics
				return returnResult(request, response, msg, 200);
			}
			
			//V1: Email recovery:
			
			//user id must be at least 4 chars, better: email
			if (userID != null && userID.length() > 4  && userID.contains("@")){
				String email = userID.trim();
				
				AccountManager auth = (AccountManager) ClassBuilder.construct(Config.accountManager_module);
				auth.setRequestInfo(request);
				JSONObject in = new JSONObject();
				JSON.add(in, "userid", email);		JSON.add(in, "type", type);
				JSONObject result = auth.requestPasswordChange(in);
				//check result for user-exists or server communication error
				if (((String) result.get("result")).equals("fail")){
					Statistics.addInternalApiHit("Endpoint:authentication:forgotPassword-error", tic);		//Statistics
					return returnResult(request, response, result.toJSONString(), 200);
				}
				//Send via email:
				//-create message
				SendEmail emailClient = (SendEmail) ClassBuilder.construct(Config.email_module);
				
				String subject = "Here is the link to change your password";
				if (language.equals(Language.DE.toValue())){
					subject = "Hier der Link zum Ändern deines Passworts";
				}
				
				String message = emailClient.loadPasswordResetMessage(language, email,
									(String) result.get("ticketid"),
									(String) result.get("token"),
									(String) result.get("time")
				);
				//-send
				int code = emailClient.send(email, message, subject, null);

				//-check result
				if (code == 0){
					//-log
					Debugger.println("password reset attempt - ID: " + email + " - timestamp: " + System.currentTimeMillis(), 3);
					//-overwrite token and return
					JSON.add(result, "token", "sent via email to " + email);
					Statistics.addInternalApiHit("Endpoint:authentication:forgotPassword", tic);			//Statistics
					return returnResult(request, response, result.toJSONString(), 200);
				}else{
					//-error
					Statistics.addInternalApiHit("Endpoint:authentication:forgotPassword-emailError", tic);		//Statistics
					if (code == 1){
						return returnResult(request, response, "{\"result\":\"fail\",\"error\":\"could not send email! Invalid address? Server problem?\"}", 200);
					}else if (code == 2){
						return returnResult(request, response, "{\"result\":\"fail\",\"error\":\"could not send email! Invalid address?\"}", 200);
					}else{
						return returnResult(request, response, "{\"result\":\"fail\",\"error\":\"could not send email! Server problem?\"}", 200);
					}
				}
			}
			Statistics.addInternalApiHit("Endpoint:authentication:forgotPassword-error", tic);		//Statistics
			return returnResult(request, response, "{\"result\":\"fail\",\"error\":\"no valid user ID found!\"}", 200);
		}
		//change password
		else if (action.trim().equals("changePassword")){
			String userID = request.queryParams("userid");
			String password = request.queryParams("new_pwd");
			String token = request.queryParams("token");
			String type = request.queryParams("type");
			String timestamp = request.queryParams("time");
			String ticketID = request.queryParams("ticketid");
			if (userID != null && password != null && token != null && timestamp != null && type != null){
				AccountManager auth = (AccountManager) ClassBuilder.construct(Config.accountManager_module);
				auth.setRequestInfo(request);
				JSONObject in = new JSONObject();
				JSON.add(in, "userid", userID.trim());		JSON.add(in, "new_pwd", password.trim());
				JSON.add(in, "token", token.trim());		JSON.add(in, "type", type);	
				JSON.add(in, "time", timestamp);			JSON.add(in, "ticketid", ticketID);
				boolean success = auth.changePassword(in);
				if (success){
					String msg = "{\"result\":\"success\",\"msg\":\"new password has been set.\"}";
					//-log
					Debugger.println("password reset - ID: " + userID + " - timestamp: " + System.currentTimeMillis(), 3);
					Statistics.addInternalApiHit("Endpoint:authentication:changePassword", tic);		//Statistics
					return returnResult(request, response, msg, 200);
				}else{
					String msg = "{\"result\":\"fail\",\"error\":\"failed to change password!\",\"code\":\"" + auth.getErrorCode() + "\"}";
					Statistics.addInternalApiHit("Endpoint:authentication:changePassword-error", tic);		//Statistics
					return returnResult(request, response, msg, 200);
				}
			}else{
				String msg = "{\"result\":\"fail\",\"error\":\"401 not authorized to change password (token?)!\",\"code\":\"" + "2" + "\"}";
				Statistics.addInternalApiHit("Endpoint:authentication:changePassword-tokenError", tic);		//Statistics
				return returnResult(request, response, msg, 200);
			}
		}
		
		//delete user
		else if (action.trim().equals("deleteUser")){
			//return returnResult(request, response, "{\"result\":\"fail\",\"error\":\"not yet implemented oO\"}", 200);
			//TODO: improve to operate more like "createUser" and delete ALL user data including the one in ElasticSearch
			
			//authenticate
			AuthenticationToken token = authenticateToken(request);
			if (!token.authenticated()){
				Statistics.addInternalApiHit("Endpoint:authentication:deleteUser-noAuth", tic);		//Statistics
				return returnNoAccess(request, response, token.getErrorCode());
				
			}else{
				//request info
				String userID = token.getUserID();
				JSONObject info = new JSONObject();
				JSON.add(info, "userid", userID);
				
				AccountManager auth = (AccountManager) ClassBuilder.construct(Config.accountManager_module);
				auth.setRequestInfo(request);
				boolean success = auth.deleteUser(info);
				
				//check result
				if (!success){
					//fail
					JSONObject msg = new JSONObject();
					JSON.add(msg, "result", "fail");
					JSON.add(msg, "error", "account could not be deleted. Please try again or contact user support!");
					JSON.add(msg, "code", auth.getErrorCode());
					Debugger.println("deleteUser for account: " + userID + " could not finish successfully!?!", 1);
					Statistics.addInternalApiHit("Endpoint:authentication:deleteUser-error", tic);		//Statistics
					return returnResult(request, response, msg.toJSONString(), 200);
				}else{
					//success
					JSONObject msg = new JSONObject();
					JSON.add(msg, "result", "success");
					JSON.add(msg, "message", "account has been deleted! Goodbye :-(");
					JSON.add(msg, "duration_ms", Debugger.toc(tic));
					Debugger.println("Account: " + userID + " has successfully been deleted :-(", 3);
					Statistics.addInternalApiHit("Endpoint:authentication:deleteUser", tic);		//Statistics
					return returnResult(request, response, msg.toJSONString(), 200);
				}
			}
		}
		//no action
		else{
			Statistics.addInternalApiHit("Endpoint:authentication:noAction", tic);		//Statistics
			return returnResult(request, response, "", 204);
		}
	} 
}
