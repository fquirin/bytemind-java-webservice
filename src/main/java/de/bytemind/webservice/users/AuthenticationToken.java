package de.bytemind.webservice.users;

import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;

import de.bytemind.core.tools.ClassBuilder;
import de.bytemind.core.tools.Converters;
import de.bytemind.core.tools.JSON;
import de.bytemind.core.users.AccountBasicInfo;
import de.bytemind.core.users.Authentication;
import de.bytemind.webservice.server.Config;
import de.bytemind.webservice.server.Credentials;
import spark.Request;

/**
 * For historical reasons this class is called "token" but it's more of a pre-step to the user account class, a bit lighter for fast access.
 * You can use it to check authentication, validate database requests etc. by using the methods included. 
 * 
 * @author Florian Quirin
 *
 */
public class AuthenticationToken {
	
	private String tokenHash = "";				//token created on demand
	private long timeCreated = 0;				//System time on creation
	private boolean authenticated = false;		//is the user authenticated?
	private String userID = "";					//user ID received from authenticator
	private String key = "-1";					//key to access micro-services and other APIs
	private String client = "web_app";			//client
	private int accessLvl = -1;					//user access level received from authenticator
	private Map<String, Object> rawBasicInfo;		//basic info of the user acquired during authentication. Note: format can depend on module that is used!
	private int errorCode;						//errorCode passed down from authenticator
	
	private Authentication auth;		//the authentication module in use, created in constructor
	
	/**
	 * Create invalid, empty token.
	 */
	public AuthenticationToken(){}
	/**
	 * Default constructor for token.
	 */
	public AuthenticationToken(Credentials credentials, Request request){
		this(credentials.getUserName(), credentials.getPassword(), credentials.getIdType(), credentials.getClient(), request);
	}
	/**
	 * Default constructor for token.
	 */
	public AuthenticationToken(String username, String password, String idType, String client, Request request){
		try {
			auth = (Authentication) ClassBuilder.construct(Config.authenticateFast_module); 	//e.g.: new Authentication_Demo();
			auth.setRequestInfo(request);
			JSONObject info = new JSONObject();
				JSON.add(info, "userId", username);
				JSON.add(info, "pwd", password);
				JSON.add(info, "idType", idType);
				JSON.add(info, "client", client);
			if (auth.authenticate(info)){
				timeCreated = System.currentTimeMillis();
				authenticated = true;
				userID = auth.getUserID();
				key = password;
				this.client = client;
				accessLvl = auth.getAccessLevel();
				rawBasicInfo = auth.getRawBasicInfo();
				errorCode = 0;
			}else{
				errorCode = auth.getErrorCode(); 
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * Constructor for test tokens.
	 */
	public AuthenticationToken(String id, Request request){
		timeCreated = System.currentTimeMillis();
		authenticated = true;
		userID = id;
		key = "pwd";
		this.client = "web_app";
		accessLvl = 0;
		rawBasicInfo = new HashMap<>();
		errorCode = 0;
	}
	
	/**
	 * Passed authentication and token are still valid?
	 * @return true/false
	 */
	public boolean authenticated(){
		if (isValid()){
			return authenticated;
		}else{
			return false;
		}
	}
	
	/**
	 * Check if the token is still valid. It remains valid for 3 minutes only!
	 * @return true/false
	 */
	public boolean isValid(){
		//TODO: is that all?
		boolean upToDate = (System.currentTimeMillis()-timeCreated) < 300000;
		if (upToDate){
			return true;
		}
		return false;
	}
	
	/**
	 * Get user ID of this user. This is the unique identifier of the user like a number or email address.
	 * Usually it is acquired during authentication.
	 * @return
	 */
	public String getUserID(){
		return userID;
	}
	
	/**
	 * Get key. Note: I have a bad feeling about this!
	 * @return key
	 */
	public String getUserKey(){
		return key;
	}
	
	/**
	 * Get user client info
	 * @return
	 */
	public String getClientInfo(){
		return client;
	}
	
	/**
	 * Get access level set during authentication.
	 * @return
	 */
	public int getAccessLevel(){
		return accessLvl;
	}
	
	/**
	 * Get basic info of the user acquired during authentication. This is useful to reduce data transfer later if this info is 
	 * acquired anyhow and already part of authentication. The "Token" contains a raw version of the data (for speed reasons) that needs to be "upgraded" to be 
	 * used in "Account" via the account manager that obtained the data.
	 * @return
	 */
	public Map<String, Object> getRawBasicInfo() {
		return rawBasicInfo;
	}
	
	public AccountBasicInfo upgradeAndGetBasicInfo() {
		return auth.upgradeBasicInfo(rawBasicInfo);
	}
	
	/**
	 * Get a secure key token for user authentication and write it to database.
	 * @param client - depending on the client different tokens can be used  
	 * @return 65char token or empty string
	 */
	public String getKeyToken(String client){
		AccountManager auth = (AccountManager) ClassBuilder.construct(Config.accountManager_module);
		tokenHash = auth.writeKeyToken(userID, client);
		errorCode = auth.getErrorCode(); 
		return tokenHash;
	}
	
	/**
	 * Logout user.
	 * @param client - depending on the client different tokens can be used
	 * @return false/true
	 */
	public boolean logoutUser(String client){
		boolean res = auth.logout(userID, client);
		errorCode = auth.getErrorCode(); 
		return res;
	}

	/**
	 * Error code passed down from authentication.
	 * 0 - no errors <br>
	 * 1 - communication error (like server did not respond)	<br>
	 * 2 - access denied (due to wrong credentials or whatever reason)	<br>
	 * 3 - might be 1 or 2 whereas 2 can also be that the parameters were wrong<br>
	 * 4 - unknown error <br>
	 * 5 - during registration: user already exists; during createUser: invalid token or time stamp	<br>
	 * @return
	 */
	public int getErrorCode(){
		return errorCode;
	}
	
	/**
	 * Export data to JSON string. Ignore password and authentication just export the data.
	 */
	public JSONObject exportJSON(){
		JSONObject data = new JSONObject();
		JSON.add(data, "userId", userID);
		JSON.add(data, "client", client);
		JSON.add(data, "timeCreated", timeCreated);
		JSON.add(data, "accessLevel", accessLvl);
		JSON.add(data, "rawBasicInfo", Converters.map2Json(rawBasicInfo));
		return data;
	}
	/**
	 * Import data from JSONObject. Ignore password and authentication just get the data.
	 */
	public void importJSON(JSONObject account){
		userID = (String) account.get("userId");
		client = (String) account.get("client");
		accessLvl = Converters.obj2int(account.get("accessLevel"), -1);
		timeCreated = Converters.obj2long(account.get("timeCreated"), 0);
		rawBasicInfo = (HashMap<String, Object>) Converters.json2HashMap(JSON.getJObject(account, "rawBasicInfo")); 	//TODO: this seems to much overhead :-/
	}
}
