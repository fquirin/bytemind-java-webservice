package de.bytemind.webservice.users;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.commons.lang.ArrayUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import de.bytemind.core.client.ClientDefaults;
import de.bytemind.core.client.Clients;
import de.bytemind.core.databases.DynamoDB;
import de.bytemind.core.statics.Language;
import de.bytemind.core.tools.Connectors;
import de.bytemind.core.tools.Converters;
import de.bytemind.core.tools.Debugger;
import de.bytemind.core.tools.InputPrompt;
import de.bytemind.core.tools.Is;
import de.bytemind.core.tools.JSON;
import de.bytemind.core.tools.Security;
import de.bytemind.core.tools.Timer;
import de.bytemind.core.users.AccountBasicInfo;
import de.bytemind.core.users.Authentication;
import de.bytemind.core.users.IdHandler;
import de.bytemind.core.users.Role;
import de.bytemind.webservice.database.DB;
import de.bytemind.webservice.server.Config;

/**
 * User authentication implemented with AWS DynamoDB.
 * 
 * @author Florian Quirin
 *
 */
public class AccountManagerDynamoDB implements AccountManager, Authentication{
	
	private static final String tableName = DB.USERS;					//table of the users
	private static final String ticketsTable = DB.TICKETS;				//table of tickets (used here to temporary store reg. tokens)
	private static final long registration_token_valid_time = 86400000L;	//that token is valid 24h
	private static final long reset_token_valid_time = 1200000L;		//that token is valid 20min
	private static final long key_token_valid_time = 86400000L;			//that token is valid for one day
	private static final long app_token_valid_time = 3153600000L;		//that token is valid for one year
	
	private static final String TOKENS_SUPP = "tokens_supp";
	private static final String TOKENS_REG = "tokens_reg";
	private static final String TOKENS_SUPP_TS = "tokens_supp_ts";		//keep this name
	private static final String TOKENS_REG_TS = "tokens_reg_ts";		//keep this name
	
	//temporary secrets
	//private static String temporaryTokenSalt = Security.getRandomUUID().replaceAll("-", "").trim();
	
	//authentication constants
	//private Request request;
	private String userID = "-1";
	private int accessLvl = -1;
	private int errorCode = 0;
	private HashMap<String, Object> rawBasicInfo;
	
	@Override
	public boolean testModule() {
		JSONObject test = DynamoDB.listTables();
		if (Connectors.httpSuccess(test)){
			JSONArray tableNames = JSON.getJArray(test, "TableNames");
			return tableNames.contains(DB.USERS);
		}else{
			return false;
		}
	}

	@Override
	public String createAdminAccount() {
		//get email
		String email = "";
		while(email.isEmpty()){
			email = InputPrompt.askString("Please enter an email address to be used for the admin (can be imaginary, but will be blocked for all registration requests: ", false);
		}
		//get password
		String password = "";
		while(password.isEmpty() || password.length() < 8){
			password = InputPrompt.askString("Please enter an admin password (at least 8 characters): ", false);
		}
		String hashedPwd = Security.hashPassword_client(password);
		//register
		boolean tmpWhitelistRestriction = Config.restrictRegistration;
		Config.restrictRegistration = false;
		JSONObject regInfo = registrationByEmail(email);
		if (regInfo == null || !JSON.getString(regInfo, "result").equals("success")){
			System.out.println("Something went wrong :-( Please check your config-file and try again! If you continue to encounter problems remove the value of 'superuser_email' in the config-file and restart.");
			JSON.printJSONpretty(regInfo);
			return "";
		}
		Config.restrictRegistration = tmpWhitelistRestriction;
		JSON.put(regInfo, "pwd", hashedPwd);
		System.out.println("One second (or two)...");
		Timer.threadSleep(500);
		//create
		if (!createUser(regInfo)){
			System.out.println("Something went wrong :-( Please check your config-file and make sure that all account-modules are working properly (there should be some test-classes to check them).");
			return "";
		}
		Timer.threadSleep(500);
		String guuid = userExists(email, IdHandler.Type.email);
		if (guuid.isEmpty()){
			System.out.println("Something went wrong, admin user could not be created :-( Please check your config-file and make sure that all account-modules are working properly (there should be some test-classes to check them).");
			return "";
		}
		//add admin roles
		HashMap<String, Object> adminRoles = new HashMap<String, Object>();
		ArrayList<Object> roles = Converters.objects2ArrayList(Role.superuser.name(), Role.chiefdev.name(), Role.seniordev.name(), 
				Role.developer.name(), Role.tester.name(), Role.translator.name(), Role.user.name());
		adminRoles.put("all", roles);
		int code = DynamoDB.writeAny(DB.USERS, AccountMapper.GUUID, guuid, new String[]{AccountMapper.ROLES}, new Object[]{adminRoles});
		if (code != 0){ 
			System.out.println("Something went wrong, admin user roles could not be set :-( Please check your config-file and make sure that all account-modules are working properly (there should be some test-classes to check them).");
			return ""; 
		}		
		//finish
		System.out.println("All set! :-) Please copy the following info to the config-file of this server (" + Config.configurationFile + "): ");
		System.out.println("superuser_id=" + guuid);
		System.out.println("superuser_email=" + email);
		System.out.println("superuser_pwd_hashed=" + hashedPwd);
		return guuid;
	}
	
	@Override
	public String createNewUserLocally(){
		//get email
		String email = "";
		while(email.isEmpty()){
			email = InputPrompt.askString("Please enter an email address for new user: ", false);
		}
		//get password
		String password = "";
		while(password.isEmpty() || password.length() < 8){
			password = InputPrompt.askString("Please enter a password (at least 8 characters): ", false);
		}
		String hashedPwd = Security.hashPassword_client(password);
		//register
		boolean tmpWhitelistRestriction = Config.restrictRegistration;
		Config.restrictRegistration = false;
		JSONObject regInfo = registrationByEmail(email);
		if (regInfo == null || !JSON.getString(regInfo, "result").equals("success")){
			System.out.println("Something went wrong :-( Please check your config-file and try again!");
			return "";
		}
		Config.restrictRegistration = tmpWhitelistRestriction;
		JSON.put(regInfo, "pwd", hashedPwd);
		System.out.println("One second (or two)...");
		Timer.threadSleep(500);
		//create
		if (!createUser(regInfo)){
			System.out.println("Something went wrong :-( Please check your config-file and make sure that all account-modules are working properly (there should be some test-classes to check them).");
			return "";
		}
		Timer.threadSleep(500);
		String guuid = userExists(email, IdHandler.Type.email);
		if (guuid.isEmpty()){
			System.out.println("Something went wrong, user could not be created :-( Please check your config-file and make sure that all account-modules are working properly (there should be some test-classes to check them).");
			return "";
		}
		System.out.println("New user with GUUID '" + guuid + "' has been created and is ready to login :-)");
		return guuid;
	}
	
	//user exists?
	@Override
	public String userExists(String identifier, String idType) throws RuntimeException{
		
		if (idType.matches(IdHandler.Type.uid + "|" + IdHandler.Type.email + "|" + IdHandler.Type.phone)){
			//all primary user IDs need to be lowerCase in DB!!!
			identifier = IdHandler.clean(identifier);
			
			//search parameters:
			JSONObject response = readBasics(identifier, idType, new String[]{AccountMapper.GUUID, AccountMapper.EMAIL, AccountMapper.PHONE});
			//System.out.println("RESPONSE: " + response.toJSONString());				//debug
			
			//Status?
			try {
				JSONObject item;
				if (response.containsKey("Items")){
					JSONArray ja = (JSONArray) response.get("Items");
					if (ja.isEmpty()){
						return "";
					}
					item = JSON.getJObject(ja, 0);
				}else{
					item = (JSONObject) response.get("Item");
					if (item == null || item.isEmpty()){
						return "";
					}
				}
				//does the result include the user ID?
				String guuid = (String) DynamoDB.typeConversion((JSONObject) item.get(AccountMapper.GUUID));
				if (guuid == null || guuid.isEmpty()){
					return "";
				}
				String otherID;
				if (idType.equals(IdHandler.Type.uid)){
					otherID = (String) DynamoDB.typeConversion((JSONObject) item.get(AccountMapper.GUUID));
				}else if (idType.equals(IdHandler.Type.email)){
					otherID = (String) DynamoDB.typeConversion((JSONObject) item.get(AccountMapper.EMAIL));
				}else if (idType.equals(IdHandler.Type.phone)){ 
					otherID = (String) DynamoDB.typeConversion((JSONObject) item.get(AccountMapper.PHONE));
				}else{
					otherID = null;
				}
				if (otherID != null && otherID.equals(identifier)){
					//System.out.println("RESULT: " + true);								//debug
					return guuid;
				}else{
					//System.out.println("RESULT: " + false);								//debug
					return "";
				}
				
			}catch (Exception ex){
				throw new RuntimeException("Authentication.userExists(...) reports 'DB query failed! Result unclear!'", ex);
			}
		}else{
			throw new RuntimeException("userExists(...) reports 'unsupported ID type' " + idType);
		}
	}
	
	//generate registration info
	@Override
	public JSONObject registrationByEmail(String email){
		//all primary user IDs need to be lowerCase in DB!!!
		email = IdHandler.clean(email);
		
		//never request an account for superuser
		if (email.equals(Config.superuserEmail.toLowerCase())){
			JSONObject result = new JSONObject();
			JSON.add(result, "result", "fail");
			JSON.add(result, "error", "request not possible, user already exists or email is invalid!");
			JSON.add(result, "code", "901");
			errorCode = 5;
			return result;
		}
		
		//type is fix here
		String type = IdHandler.Type.email;
				
		//check if user exists
		//------------------------------------
		String guuid = userExists(email, type);
		if (!guuid.isEmpty()){
			JSONObject result = new JSONObject();
			JSON.add(result, "result", "fail");
			JSON.add(result, "error", "request not possible, user already exists!");	//REMEMBER: keep "already exists" for client
			JSON.add(result, "code", "901");
			errorCode = 5;
			return result;
		}
		//------------------------------------
		
		//check if user is allowed to register
		//------------------------------------
		if (Config.restrictRegistration){
			if (!DB.searchWhitelistUserEmail(email)){
				JSONObject result = new JSONObject();
				JSON.add(result, "result", "fail");
				JSON.add(result, "error", "request not possible, user (email) is not allowed to create new account!"); //REMEMBER: keep "not allowed" for client
				JSON.add(result, "code", "902");
				errorCode = 5;
				return result;
			}
		}
		//------------------------------------
		
		long time = System.currentTimeMillis();
		String token, ticketId;
		try{
			token = getRandomSecureToken();
			ticketId = UserIdGenerator.getGuidGenerator().getTicketGUID();
			String storeToken = Security.hashPassword_client(email + token + time + ticketId);
			String[] keys = new String[]{TOKENS_REG, TOKENS_REG_TS};
			Object[] objects = new Object[]{storeToken, new Long(time)};
			if (!write_reg_token(ticketId, keys, objects)){
				throw new RuntimeException("registration token storing failed!");
			}
			
		}catch(Exception e){
			JSONObject result = new JSONObject();
			JSON.add(result, "result", "fail");
			JSON.add(result, "error", "500 - token generation or storing failed! - msg: " + e.getMessage());
			errorCode = 4;
			return result;
		}
		//use this in Email:
		/*
		String url = Config.endpoint_url_createUser 
			+ "?userid=" + encodeURIComponent(userid)
			+ "&ticketid=" + encodeURIComponent(ticketid) 
			+ "&time=" + encodeURIComponent(timeStamp) 
			+ "&token=" + encodeURIComponent(token)
			+ "&type=" + encodeURIComponent(type);
		 */
		JSONObject result = new JSONObject();
		JSON.add(result, "result", "success");
		JSON.add(result, "token", token);
		JSON.add(result, "time", String.valueOf(time));
		JSON.add(result, "userid", email);
		JSON.add(result, "type", type);
		JSON.add(result, "ticketid", ticketId);
		//JSON.add(result, "url", url);
		errorCode = 0;
		return result;
	}

	//create a new user - fields userid, pwd, time, token
	@Override
	public boolean createUser(JSONObject info) {
		//check type
		String type = (String) info.get("type");
		
		//---V1: create via email registration:
		if (type != null && type.equals(IdHandler.Type.email)){
			String email = "";
			String ticketId = "";
			String language = "";
			try{
				language = (String) info.get("language");	if (Is.nullOrEmpty(language)) language = Language.EN.toValue();
				String token = (String) info.get("token");		
				String ts = (String) info.get("time");
				email = (String) info.get("userid");		
				ticketId = (String) info.get("ticketid");
				long time = Long.parseLong(ts);
				//System.out.println("createUser - token: " + token + ", timeStamp: " + time);		//debug
				
				//check if the token is still valid - first time, might still be fake but might also block a lot of real calls
				if ((System.currentTimeMillis() - time) > registration_token_valid_time){
					errorCode = 5;
					return false;
				}
			
				//all primary user IDs need to be lowerCase in DB!!!
				email = IdHandler.clean(email);
	
				//check if token is as expected
				Object[] res = readSupportToken(ticketId, TOKENS_REG);
				String sendToken = Security.hashPassword_client(email + token + time + ticketId);
				String tokenTarget = (String) res[0];
				long timeTarget = (long) res[1];
				if (sendToken.length() > 15 && sendToken.equals(tokenTarget) && (timeTarget == time)){
					//continue
				}else{
					errorCode = 5; 
					return false;
				}
				
			}catch (Exception e){					
				errorCode = 5; 
				return false;	
			}
			
			//everything is fine, so create user
			String pwd = (String) info.get("pwd"); 
			
			//check if the password is valid
			if (pwd == null || pwd.length() < 8){ 		//Note: since we work on the hashed password this will always be true :-( Need to do in client!
				errorCode = 6;
				return false;
			}
			
			//delete old token or make it invalid
			write_reg_token(ticketId, new String[]{TOKENS_REG_TS}, new Object[]{new Long(0)});
			
			//get a new ID and create a save storage for the password by standard PBKDF2 HMAC-SHA-256 algorithm
			String[] ids = {email, "-"};   	//additionally known ids due to type of login. Order fixed: email, phone, xy, ...
			return createUserAction(language, pwd, ids);
		
		//other ID types of creating user
		}else{
			throw new RuntimeException("createUser(...) reports 'unsupported registration type' " + type);
		}
	}
	/**
	 * This is the action that executes the createUser request after it has been checked.
	 */
	public boolean createUserAction(String language, String pwd, String... ids){
		//get a new ID and create a save storage for the password by standard PBKDF2 HMAC-SHA-256 algorithm
		String guuid, salt;
		int iterations;
		try{
			UserIdGenerator gen = new UserIdGenerator(pwd);
			guuid = gen.guuid;
			iterations = gen.iterations;
			salt = gen.salt;
			pwd = gen.pwd;
			//System.out.println("createUser - userid: " + guuid + ", token: " + token + ", timeStamp: " + time);		//debug
		
		}catch (Exception e){
			Debugger.println(e.getMessage(), 1); 	//debug
			errorCode = 7;
			return false;
		}			
		
		//default keys 'n values:
		
		//------------------- THIS DEFINES THE BASIC STRUCTURE OF THE USER ITEM --------------------
		//Dummies
		//String phone = "";
		HashMap<String, Object> dummyMap = new HashMap<String, Object>();		//empty dummy
		HashMap<String, Object> dummyRoles = new HashMap<String, Object>();		//roles dummy
		ArrayList<String> roles = new ArrayList<>();	
		roles.add("user");		dummyRoles.put("all", roles);
		
		ArrayList<String> basicKeys = new ArrayList<>();
		ArrayList<Object> basicObjects = new ArrayList<>();
		//basicKeys.add(ACCOUNT.GUUID);				basicObjects.add(guuid);
		basicKeys.add(AccountMapper.PASSWORD);			basicObjects.add(pwd);
		basicKeys.add(AccountMapper.PWD_SALT);			basicObjects.add(salt);
		basicKeys.add(AccountMapper.PWD_ITERATIONS);	basicObjects.add(iterations);
		basicKeys.add(AccountMapper.LANGUAGE);			basicObjects.add(language);
		basicKeys.add(AccountMapper.TOKENS);			basicObjects.add(dummyMap);
		basicKeys.add(AccountMapper.STATISTICS);		basicObjects.add(dummyMap);
		basicKeys.add(AccountMapper.USER_NAME);			basicObjects.add(dummyMap);
		basicKeys.add(AccountMapper.ROLES);				basicObjects.add(dummyRoles);
		//basicKeys.add(AccountMapper.ADDRESSES);			basicObjects.add(dummy_map);
		basicKeys.add(AccountMapper.INFOS);				basicObjects.add(dummyMap);
		//IDs
		basicKeys.add(AccountMapper.EMAIL);				basicObjects.add(ids[0]); 		//ids[0] = email;
		basicKeys.add(AccountMapper.PHONE);				basicObjects.add(ids[1]); 		//ids[1] = phone;
		//add more basics from mapper
		for (Entry<String, Object> e : AccountMapper.addCreationBasics.entrySet()){
			basicKeys.add(e.getKey());					basicObjects.add(e.getValue());
		}
		//collect
		String[] keys = basicKeys.toArray(new String[1]);
		Object[] objects = basicObjects.toArray(new Object[1]);
		//-------------------------------------------------------------------------------------------
		
		//write values and return true/false - error codes can be checked afterwards if necessary
		boolean success = write_protected(guuid, IdHandler.Type.uid, keys, objects);
		return success;
	}
	
	//request change of password
	@Override
	public JSONObject requestPasswordChange(JSONObject info){
		//get parameters
		String userid = (String) info.get("userid");
		String type = (String) info.get("type");
		
		//check reset type
		if (type == null || !type.equals(IdHandler.Type.email)){
			throw new RuntimeException("requestPasswordChange(...) reports 'unsupported reset type' " + type);
		}
		
		//---V1: Email reset
		
		//all primary user IDs need to be lowerCase in DB!!!
		userid = IdHandler.clean(userid);
		
		//check if user exists
		//------------------------------------
		String guuid = userExists(userid, type);
		if (guuid.isEmpty()){
			JSONObject result = new JSONObject();
			JSON.add(result, "result", "fail");
			JSON.add(result, "error", "request not possible, user cannot be found!");
			errorCode = 5;
			return result;
		}
		//------------------------------------
		
		long time = System.currentTimeMillis();
		String token, ticketId;
		try{
			token = getRandomSecureToken();
			ticketId = UserIdGenerator.getGuidGenerator().getTicketGUID();
			String storeToken = Security.hashPassword_client(userid + token + time + ticketId);
			String[] keys = new String[]{TOKENS_SUPP, TOKENS_SUPP_TS};
			Object[] objects = new Object[]{storeToken, new Long(time)};
			if (!write_reg_token(ticketId, keys, objects)){
				throw new RuntimeException("requestPasswordChange token storing failed!");
			}
			
		}catch(Exception e){
			JSONObject result = new JSONObject();
			JSON.add(result, "result", "fail");
			JSON.add(result, "error", "500 - token generation or storing failed!");
			errorCode = 4;
			return result;
		}

		JSONObject result = new JSONObject();
		JSON.add(result, "result", "success");
		JSON.add(result, "token", token);
		JSON.add(result, "time", String.valueOf(time));
		JSON.add(result, "userid", userid);
		JSON.add(result, "type", type);
		JSON.add(result, "ticketid", ticketId);
		//JSON.add(result, "url", url);
		errorCode = 0;
		return result;
	}
	
	//change the password - fields userid, type, new_pwd, time, token
	@Override
	public boolean changePassword(JSONObject info) {
		//check type
		String type = (String) info.get("type");
		if (type == null || !type.equals(IdHandler.Type.email)){
			throw new RuntimeException("changePassword(...) reports 'unsupported type' " + type);
		}
		
		//---V1: change via email confirmation:
		
		String email = "";
		String guuid = "";
		String ticketId = "";
		try{
			String token = (String) info.get("token");		
			String ts = (String) info.get("time");
			email = (String) info.get("userid");		
			ticketId = (String) info.get("ticketid");
			long time = Long.parseLong(ts);
			//System.out.println("createUser - token: " + token + ", timeStamp: " + time);		//debug
			
			//check if the token is still valid - first time, might still be fake but might also block a lot of real calls
			if ((System.currentTimeMillis() - time) > reset_token_valid_time){
				errorCode = 5;
				return false;
			}
		
			//all primary user IDs need to be lowerCase in DB!!!
			email = IdHandler.clean(email);
			
			//never request a new password for the superuser
			if (email.equals(Config.superuserEmail.toLowerCase())){
				errorCode = 5;
				return false;
			}
			
			//check if user exists ... again just to be sure and to get GUUID.
			//------------------------------------
			guuid = userExists(email, type);
			if (guuid.isEmpty()){
				errorCode = 2;
				return false;
			}
			//------------------------------------

			//check if token is as expected
			Object[] res = readSupportToken(ticketId, TOKENS_SUPP);
			String sendToken = Security.hashPassword_client(email + token + time + ticketId);
			String tokenTarget = (String) res[0];
			long timeTarget = (long) res[1];
			if (sendToken.length() > 15 && sendToken.equals(tokenTarget) && (timeTarget == time)){
				//continue
			}else{
				errorCode = 5; 
				return false;
			}
			
		}catch (Exception e){					
			errorCode = 5; 
			return false;	
		}
		
		//everything is fine, so create password
		String pwd = (String) info.get("new_pwd"); 
		
		//check if the password is valid
		if (pwd == null || pwd.length() < 8){
			errorCode = 6;
			return false;
		}
		
		//get a new ID and create a save storage for the new password
		String salt;
		int iterations;
		try{
			UserIdGenerator gen = new UserIdGenerator(pwd);
			iterations = gen.iterations;
			salt = gen.salt;
			pwd = gen.pwd;
			//System.out.println("createUser - userid: " + guuid + ", token: " + token + ", timeStamp: " + time);		//debug
		
		}catch (Exception e){
			Debugger.println(e.getMessage(), 1); 	//debug
			errorCode = 7;
			return false;
		}
		
		//change password 	
		String[] keys = new String[]{AccountMapper.PASSWORD, AccountMapper.PWD_SALT, AccountMapper.PWD_ITERATIONS};
		Object[] objects = new Object[]{pwd, salt, iterations};
		
		//delete old token or make it invalid
		write_reg_token(ticketId, new String[]{TOKENS_SUPP_TS}, new Object[]{new Long(0)});
		
		//-------------------------------------------------------------------------------------------

		//write values and return true/false - error codes can be checked afterwards if necessary
		return write_protected(guuid, IdHandler.Type.uid, keys, objects);
	}
	
	//delete user - fields userid
	@Override
	public boolean deleteUser(JSONObject info) {
		//TODO: make it like "createUser" ...
		
		//get user ID
		String userid = (String) info.get("userid");
		if (userid == null || userid.isEmpty()){
			errorCode = 4;
			return false;
		}else{
			userid = IdHandler.clean(userid);
		}
		
		//operation:
		String operation = "DeleteItem";
		
		//primaryKey:
		JSONObject prime = DynamoDB.getPrimaryUserKey(userid);
		
		//JSON request:
		JSONObject request = new JSONObject();
		JSON.add(request, "TableName", tableName);
		JSON.add(request, "Key", prime);
		JSON.add(request, "ReturnValues", "NONE");
		
		//System.out.println("REQUEST: " + request.toJSONString());		//debug
		
		//Connect
		JSONObject response = DynamoDB.request(operation, request.toJSONString());
		//System.out.println("RESPONSE: " + response.toJSONString());			//debug
		//System.out.println("Time needed: " + Debugger.toc(tic) + "ms");		//debug
		
		if (!Connectors.httpSuccess(response)){
			errorCode = 3;
			Debugger.println("deleteUser() - DynamoDB Response: " + response.toJSONString(), 1);			//debug
			return false;
		}else{
			//note: no stats recorded here - actually we don't really know if the user was deleted
			errorCode = 0;
			return true;
		}	
	}
	
	//request meta info
	@Override
	public void setRequestInfo(Object request) {
		//this.request = (Request) request;
		//currently not needed
	}

	//check it!
	@Override
	public boolean authenticate(JSONObject info) {
		String username = (String) info.get("userId");
		String password = (String) info.get("pwd");
		String idType = (String) info.get("idType");
		String client = (String) info.get("client");
		
		//use the DynamoDB access to read 
		//-------------BASICS-------------
		username = IdHandler.clean(username);
		String[] essentialBasics = new String[]{
				//REQUIRED:
				AccountMapper.GUUID, AccountMapper.PASSWORD, AccountMapper.PWD_SALT, AccountMapper.PWD_ITERATIONS, AccountMapper.TOKENS
		};
		String[] requiredBasics = new String[]{
				AccountMapper.ROLES
		};
		String[] returnBasics = (String[]) ArrayUtils.addAll(requiredBasics, AccountMapper.addReadBasics);
		String[] readBasics = (String[]) ArrayUtils.addAll(essentialBasics, returnBasics);
		/* e.g.:
		AccountMapper.GUUID, AccountMapper.PASSWORD, AccountMapper.PWD_SALT, AccountMapper.PWD_ITERATIONS, AccountMapper.TOKENS, 
		AccountMapper.EMAIL, AccountMapper.PHONE, AccountMapper.ROLES,
		AccountMapper.USER_NAME, AccountMapper.LANGUAGE, AccountMapper.USER_BIRTH
		 */
		JSONObject result = readBasics(username, idType, readBasics);
		//System.out.println("Auth. req: " + username + ", " + idType + ", " + password); 		//debug
		//System.out.println("Auth. res: " + result.toJSONString()); 		//debug
		
		//Status?
		if (!Connectors.httpSuccess(result)){
			errorCode = 3;
			return false;
		
		}else{
			JSONObject item;
			if (result.containsKey("Items")){
				JSONArray ja = (JSONArray) result.get("Items");
				if (ja.isEmpty()){
					errorCode = 2;
					return false;
				}
				item = JSON.getJObject(ja, 0);
			}else{
				item = (JSONObject) result.get("Item"); 
				if (item == null || item.isEmpty()){
					errorCode = 2;
					return false;
				}
			}
			//check password or key token
			String pwd;
			//token key
			if (password.length() == 65){
				String token = getAppTokenPath(client);
				String token_ts = token + "_ts";
				long valid_time = app_token_valid_time;
				if (Clients.isRatherUnsafe(client)){
					valid_time = key_token_valid_time;
				}
				JSONObject t = DynamoDB.dig(item, token);
				if (t != null){
					pwd = DynamoDB.typeConversion(t).toString();
					//check time stamp
					//long ts = (long)(double)(Account_DynamoDB.typeConversion(Account_DynamoDB.dig(item, ACCOUNT.TOKEN_KEY_TS)));
					JSONObject tts = DynamoDB.dig(item, token_ts);
					long ts = Converters.obj2long(DynamoDB.typeConversion(tts), -1);
					if ((System.currentTimeMillis() - ts) > valid_time){
						//token became invalid
						pwd = null;
					}
				}else{
					pwd = null;
				}
			//original key - this is the login that generates the token later and should not be abused for client-authentication via real password
			}else{
				try {
					pwd = (String) DynamoDB.typeConversion((JSONObject) item.get(AccountMapper.PASSWORD));
					String salt = (String) DynamoDB.typeConversion((JSONObject) item.get(AccountMapper.PWD_SALT));
					int iterations = Converters.obj2int(DynamoDB.typeConversion((JSONObject) item.get(AccountMapper.PWD_ITERATIONS)), -1);
					UserIdGenerator gen = new UserIdGenerator(password, salt, iterations);
					password = gen.pwd;
				} catch (Exception e) {
					Debugger.println("Authentication_DynamoDB.authenticate(...) - using original password failed due to: " + e.getMessage(), 1); 	//debug
					if (e.getStackTrace() != null && e.getStackTrace().length > 0){
						Debugger.println("Authentication_DynamoDB.authenticate(...) - error last trace: " + e.getStackTrace()[0], 1);
					}
					errorCode = 7;
					return false;
				}
			}
			if (pwd != null && !pwd.trim().isEmpty() && password != null && !password.trim().isEmpty()){
				//check
				if (!pwd.equals(password)){
					errorCode = 2;
					return false;
				
				}else{
					//authentication successful!
					userID = (String) DynamoDB.typeConversion((JSONObject) item.get(AccountMapper.GUUID));
					accessLvl = 0;			//basic auth does level 0
					errorCode = 0;			//no errors so far
										
					//---------now get basic info too----------
					//this is the quick version that does not convert anything except the user roles just copies the raw objects
					
					rawBasicInfo = new HashMap<String, Object>();
					
					//fill with raw data
					for (String key : returnBasics){
						rawBasicInfo.put(key, DynamoDB.dig(item, key));
					}					
					//-----------------------------------------
					
					return true;
				}
				
			}else{
				errorCode = 2;
				return false;
			}
		}
	}
	
	//create and return key token
	@Override
	public String writeKeyToken(String userid, String client) {
		//create a random string
		String userToken;
		try {
			userToken = getRandomSecureToken();
			
		} catch (Exception e) {
			Debugger.println("writeKeyToken(..) - failed to create secure token! Id: " + userid + ", client: " + client, 1);
			e.printStackTrace();
			errorCode = 3;
			return "";
		}
		//write server token
		String tokenPath = getAppTokenPath(client);
		boolean success = writeLoginToken(userid, IdHandler.Type.uid, userToken, tokenPath);
		if (success && !userToken.isEmpty()){
			errorCode = 0;
			return userToken;
		}else{
			errorCode = 3;
			return "";
		}
	}

	//logout user and make token invalid
	@Override
	public boolean logout(String userid, String client) {
		//delete key token
		String token = getAppTokenPath(client);
		String[] keys = new String[]{token};
		Object[] objects = new Object[]{"-"};
		//String[] keys = new String[]{ACCOUNT.TOKEN_KEY, ACCOUNT.TOKEN_KEY_TS};
		//Object[] objects = new Object[]{"-", new Long(0)};
		boolean success = write_protected(userid, IdHandler.Type.uid, keys, objects); 	//logout should be called with GUUID type
		if (success){
			errorCode = 0;
			return true;
		}else{
			errorCode = 3;
			return false;
		}
	}
	//logout all clients 
	@Override
	public boolean logoutAllClients(String userid) {
		//TODO: delete all key tokens
		return false;
	}
	
	//return ID
	@Override
	public String getUserID() {
		return userID;
	}

	//return access level
	@Override
	public int getAccessLevel() {
		return accessLvl;
	}
	
	//return error code
	@Override
	public int getErrorCode() {
		return errorCode;
	}
	
	//get basic info
	@Override
	public HashMap<String, Object> getRawBasicInfo() {
		return rawBasicInfo;
	}
	
	@Override
	public AccountBasicInfo upgradeBasicInfo(Map<String, Object> rawBasicInfo) {
		JSONObject basicInfoJson = new JSONObject();
		
		for (Entry<String, Object> entry : rawBasicInfo.entrySet()){
			String key = entry.getKey();
			//ROLES handler
			if (key.equals(AccountMapper.ROLES)){
				Object foundRoles = DynamoDB.typeConversion((JSONObject) entry.getValue());
				if (foundRoles != null){
					Map<String, Object> roles = Converters.object2HashMap_SO(foundRoles);
					if (roles != null && roles.containsKey("all")){
						JSON.put(basicInfoJson, AccountMapper.ROLES, Converters.list2JsonArray((List<?>) roles.get("all")));
					}
				}
			//NAME handler
			}else if (key.equals(AccountMapper.USER_NAME)){
				Object foundName = DynamoDB.typeConversion((JSONObject) entry.getValue());
				if (foundName != null){
					Map<String, Object> name = Converters.object2HashMap_SO(foundName);
					if (name != null && !name.isEmpty()){
						JSON.put(basicInfoJson, AccountMapper.USER_NAME, Converters.map2Json(name));
					}
				}
			
			//REST handler
			}else{
				Object val = DynamoDB.typeConversion((JSONObject) entry.getValue());
				if (val == null){
					JSON.put(basicInfoJson, key, "");
				}else{
					JSON.put(basicInfoJson, key, val);
				}
			}
		}
		
		AccountBasicInfo basicInfo = new AccountBasicInfo(basicInfoJson);
		return basicInfo;
	}
	
	/**
	 * Get the proper path to the token used by this client (based on client_info).
	 * @param client - client_info as sent by user
	 * @return
	 */
	private String getAppTokenPath(String client){
		if (client == null || client.isEmpty()){
			client = ClientDefaults.client_info;
		}
		//String token_path = ACCOUNT.TOKENS + "." + client.replaceFirst("_v\\d.*?\\d(_|$)", "_").trim().replaceFirst("_$", "").trim();
		String token_path = AccountMapper.TOKENS + "." + client.replaceFirst("_v\\d.*?(_|$)", "_").trim().replaceFirst("_$", "").replaceAll("[\\W]", "").trim();
		//System.out.println("CLIENT TOKEN: " + token_path); 		//debug
		return token_path;
	}
	
	//-----------------------Common tools--------------------------
	
	/**
	 * Generate a token for the user creation and password change process.
	 * @return random secure token
	 * @throws Exception 
	 */
	private static String getRandomSecureToken() throws Exception{
		//create a random string
		String base = UUID.randomUUID().toString().replaceAll("-", "").substring(16);
		String salt = new String(Security.getRandomSalt(32), "UTF-8");
		String pepper = "5cd19a84ec46";
		//add the current time
		long now = System.currentTimeMillis();
		//do the user hash
		String secureToken = Security.hashPassword_client(base + pepper + now + salt);
		//add one character to distinguish
		secureToken = secureToken + "a";
		if (secureToken.length() < 16){
			throw new RuntimeException("Failed to create secure token!");
		}
		return secureToken;
	}
	
	//------------------------Connection---------------------------
	
	/**
	 * Write token to server (userId, idType) at tokenPath with time stamp.
	 * @return true/false
	 */
	private boolean writeLoginToken(String userid, String idType, String token, String tokenPath){
		if (token == null || token.length() < 16 || !tokenPath.startsWith(AccountMapper.TOKENS)){
			Debugger.println("writeSecureToken(..) failed! Either because of wrong token or wrong path.", 1);
			return false;
		}
		long now = System.currentTimeMillis();
		String tokenPath_ts = tokenPath + "_ts";
		String[] keys = new String[]{tokenPath, tokenPath_ts};
		Object[] objects = new Object[]{token, new Long(now)};
		
		return write_protected(userid, idType, keys, objects);
	}
	/**
	 * Read token from ticket server with ticketId at tokenPath with time stamp.
	 * @return Object[] with [0]:token (string), [1]:token_ts (long) or null on error
	 */
	private Object[] readSupportToken(String ticketid, String tokenPath){
		if (!tokenPath.startsWith(AccountMapper.TOKENS)){
			Debugger.println("readSecureToken(..) failed because of invalid path!", 1);
			return null;
		}
		String tokenPath_ts = tokenPath + "_ts";
		String[] keys = new String[]{tokenPath, tokenPath_ts};
		JSONObject res = read_reg_token(ticketid, keys);
		if (!Connectors.httpSuccess(res)){
			Debugger.println("readSupportToken(..) failed because the server request was not succesful!", 1);
			return null;
		}
		try{
			JSONObject item = (JSONObject) res.get("Item");
			JSONObject t = DynamoDB.dig(item, tokenPath);
			JSONObject tts = DynamoDB.dig(item, tokenPath_ts);
			String token = DynamoDB.typeConversion(t).toString();
			long token_ts = Converters.obj2long(DynamoDB.typeConversion(tts), -1);
			return new Object[]{token, token_ts};
			
		}catch (Exception e){
			Debugger.println("readSupportToken(..) failed! Either the ticketID was not found or had no expected tokens!", 1);
			return null;
		}
	}
	
	/**
	 * Response of DynamoDB to basic info POST request. This is the only method that should be able to retrieve secret info like passwords and tokens. 
	 * Connectors.httpSuccess(result) can be used to check for POST status.
	 * @param userID - unique id, often email address
	 * @param idType - ID type 
	 * @param lookUp - array of strings to retrieve
	 * @return JSONObject with result (check yourself for usefulness)
	 */
	private JSONObject readBasics(String userID, String idType, String[] lookUp){
		JSONObject response;
		if (idType.equals(IdHandler.Type.uid)){
			//UID
			response = DynamoDB.getItem(tableName, AccountMapper.GUUID, userID, lookUp);
		}else if (idType.equals(IdHandler.Type.email)){
			//EMAIL
			response = DynamoDB.queryIndex(tableName, AccountMapper.EMAIL, userID, lookUp);
		}else if (idType.equals(IdHandler.Type.phone)){
			//PHONE
			response = DynamoDB.queryIndex(tableName, AccountMapper.PHONE, userID, lookUp);
		}else{
			throw new RuntimeException("Authentication.read_basics(...) reports 'unsupported identifier type': " + idType);
		}
		//System.out.println("Time needed: " + Debugger.toc(tic) + "ms");		//debug
		return response;
	}
	private JSONObject read_reg_token(String ticketID, String[] lookUp){
		return DynamoDB.getItem(ticketsTable, DynamoDB.PRIMARY_TICKET_KEY, ticketID, lookUp);
	}
	
	/**
	 * Write a protected account attribute. For server operations only!!!
	 * @param userID - account ID, often email address
	 * @param idType - ID type
	 * @param keys - keys to write
	 * @param objects - values to put
	 * @return write success true/false
	 */
	private boolean write_protected(String userID, String idType, String[] keys, Object[] objects){
		if (idType.equals(IdHandler.Type.uid)){
			//UID
			errorCode = DynamoDB.writeAny(tableName, AccountMapper.GUUID, userID, keys, objects);
		/* TODO: fix!
		}else if (idType.equals(ID.Type.email)){
			//EMAIL
			errorCode = DynamoDB.writeAny(tableName, ACCOUNT.EMAIL, userID, keys, objects);
		}else if (idType.equals(ID.Type.phone)){
			//PHONE
			errorCode = DynamoDB.writeAny(tableName, ACCOUNT.PHONE, userID, keys, objects);
		*/
		}else{
			throw new RuntimeException("Authentication.write_protected(...) reports 'unsupported identifier type': " + idType);
		}
		if (errorCode == 0){
			return true;
		}else{
			return false;
		}	
	}
	private boolean write_reg_token(String ticketID, String[] keys, Object[] objects){
		errorCode = DynamoDB.writeAny(ticketsTable, DynamoDB.PRIMARY_TICKET_KEY, ticketID, keys, objects);
		if (errorCode == 0){
			return true;
		}else{
			return false;
		}	
	}
	
}
