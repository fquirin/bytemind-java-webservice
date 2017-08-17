package de.bytemind.webservice.users;

import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;

import de.bytemind.core.tools.ClassBuilder;
import de.bytemind.core.tools.Converters;
import de.bytemind.core.tools.DateTime;
import de.bytemind.core.tools.Debugger;
import de.bytemind.core.tools.JSON;
import de.bytemind.core.tools.Security;
import de.bytemind.core.tools.Timer;
import de.bytemind.core.users.Account;
import de.bytemind.core.users.AccountBasicInfo;
import de.bytemind.core.users.IdHandler;
import de.bytemind.core.users.Role;
import de.bytemind.webservice.server.BasicAuthenticationServer;
import de.bytemind.webservice.server.ByteMindServer;
import de.bytemind.webservice.server.Config;
import de.bytemind.webservice.server.Credentials;
import de.bytemind.webservice.server.FakeRequest;

public class AccountTestSequence {
	
	public static void main(String[] args) {
		
		//Choose a server
		ByteMindServer server = new BasicAuthenticationServer();
		
		//Load server settings from file (and arguments)
		server.loadSettings(args);
		Config.restrictRegistration = false; 	//allow all emails for registration
		
		//Set modules
		AccountManager am = (AccountManager) ClassBuilder.construct(Config.accountManager_module);
		AccountData ad = (AccountData) ClassBuilder.construct(Config.accountData_module);
		
		//Test-user
		String guuid = "";
		String email = "test@example.com";
		String password = "test12345";
		String passwordHash = Security.hashPassword_client(password);
		String resetPassword = "test123456";
		String resetPasswordHash = Security.hashPassword_client(resetPassword);
		String firstName = "Tester";
		String lastName = "Testmann";
		String nickName = "Testy";
		String language = "de";
		
		//Create new user
		JSONObject regInfo = am.registrationByEmail(email);
		if (!regInfo.containsKey("token")){
			JSON.printJSONpretty(regInfo);
			throw new RuntimeException("registrationByEmail FAILED!");
		}
		JSON.add(regInfo, "pwd", passwordHash);
		Timer.threadSleep(500);
		if (!am.createUser(regInfo)){
			throw new RuntimeException("createUser FAILED!");
		}
		Timer.threadSleep(500);
		guuid = am.userExists(email, IdHandler.Type.email);
		if (guuid.isEmpty()){
			throw new RuntimeException("userExists FAILED!");
		}else{
			System.out.println("\nCreated user: " + guuid);
		}
		
		//Authenticate user - classical username-password version (this should only be used for special situations because its the most expensive way)
		long tic = Timer.tic();
		FakeRequest request1 = new FakeRequest("GUUID=" + guuid, "PWD=" + password);
		Credentials credentialsV1 = new Credentials(request1);
		if (!am.authenticate(credentialsV1.getAuthJSON())){
			throw new RuntimeException("authenticate - username/password - FAILED!");
		}else{
			System.out.println("Authenticated - GUUID/PWD - took: " + Timer.toc(tic) + "ms");
		}
		//Authenticate user - classical KEY version (this should be used for logins to generate a token, its a bit safer since the "real" password is already hashed)
		tic = Timer.tic();
		FakeRequest request2 = new FakeRequest("KEY=" + (guuid + ";" + passwordHash));
		Credentials credentialsV2 = new Credentials(request2);
		if (!am.authenticate(credentialsV2.getAuthJSON())){
			throw new RuntimeException("authenticate - key - FAILED!");
		}else{
			System.out.println("Authenticated - KEY - took: " + Timer.toc(tic) + "ms");
		}
		//Get a token
		tic = Timer.tic();
		AuthenticationToken userToken1 = new AuthenticationToken(credentialsV2, request2);
		if (!userToken1.authenticated()){
			throw new RuntimeException("authenticate - AuthenticationToken (hashed pwd) - FAILED!");
		}else{
			System.out.println("Authenticated - AuthenticationToken (hashed pwd) - took: " + Timer.toc(tic) + "ms");
		}
		String keyToken = userToken1.getKeyToken(credentialsV2.getClient());
		if (keyToken.isEmpty()){
			throw new RuntimeException("getKeyToken FAILED!");
		}
		//Authenticate user - common KEY version with temp. token (this should be used most of the time to communicate with server after login)
		tic = Timer.tic();
		FakeRequest request3 = new FakeRequest("KEY=" + (guuid + ";" + keyToken));
		Credentials credentialsV3 = new Credentials(request3);
		AuthenticationToken userToken2 = new AuthenticationToken(credentialsV3, request3);
		if (!userToken2.authenticated()){
			throw new RuntimeException("authenticate - AuthenticationToken (temp. keyToken) - FAILED!");
		}else{
			System.out.println("Authenticated - AuthenticationToken (temp. keyToken) - took: " + Timer.toc(tic) + "ms");
			System.out.println("------user token------");
			JSON.printJSONpretty(userToken2.exportJSON());
			System.out.println("------------\n");
		}
		
		//Convert user token to Account
		Account account = new Account(userToken2.getUserID(), userToken2.getAccessLevel(), userToken2.getClientInfo());
		Map<String, Object> rawBasicInfo = userToken2.getRawBasicInfo();
		System.out.println("------raw basic info------");
		Debugger.printMap_SO(rawBasicInfo);
		AccountBasicInfo abi = am.upgradeBasicInfo(rawBasicInfo);
		System.out.println("\n------account basic info------");
		JSON.printJSONpretty(abi.exportJson());
		account.mapBasicInfo(abi);
		System.out.println("\n------account------");
		JSON.printJSONpretty(account.exportJSON());
		System.out.println("------------\n");
		
		//Add some data
		int code = ad.setInfoObject(account, AccountMapper.USER_NAME_FIRST, firstName);
		if (code != 0){
			throw new RuntimeException("setInfoObject (1) FAILED!");
		}
		code = ad.setInfos(account, JSON.make(AccountMapper.USER_NAME_LAST, lastName, AccountMapper.USER_NAME_NICK, nickName, 
				AccountMapper.USER_BIRTH, DateTime.getUTC(DateTime.ISODateFormat), AccountMapper.USER_GENDER, "m",
				AccountMapper.LANGUAGE, language));
		if (code != 0){
			throw new RuntimeException("setInfos (2) FAILED!");
		}
		code = ad.setInfos(account, JSON.make(AccountMapper.PASSWORD, "unsafe12345", AccountMapper.EMAIL, "new@example.com"));
		if (code == 0){
			throw new RuntimeException("setInfos (3) DID NOT FAIL (as it should have)!");
		}
		List<Object> modRolesList = Converters.jsonArray2ArrayList(account.getUserRoles());
		modRolesList.add(Role.superuser);
		code = ad.setInfos(account, JSON.make(AccountMapper.ROLES, modRolesList));
		if (code == 0){
			throw new RuntimeException("setInfos (4) DID NOT FAIL (as it should have)!");
		}
		Timer.threadSleep(500);
		
		//Check new data
		userToken2 = new AuthenticationToken(credentialsV3, request3);
		if (!userToken2.authenticated()){
			throw new RuntimeException("authenticate - AuthenticationToken (temp. keyToken) - FAILED!");
		}else{
			/*
			System.out.println("\n------raw------");
			Debugger.printMap_SO(userToken2.getRawBasicInfo());
			System.out.println("\n------upgrade------");
			JSON.printJSONpretty(am.upgradeBasicInfo(userToken2.getRawBasicInfo()).exportJson());
			*/
			System.out.println("\n------account------");
			account = new Account(userToken2.getUserID(), userToken2.getAccessLevel(), userToken2.getClientInfo());
			account.mapBasicInfo(am.upgradeBasicInfo(userToken2.getRawBasicInfo()));
			JSON.printJSONpretty(account.exportJSON());
			System.out.println("------------\n");
		}
		Object custom = ad.getInfoObject(account, AccountMapper.USER_GENDER);
		if (custom == null){
			throw new RuntimeException("getInfoObject (1) FAILED!");
		}else{
			System.out.println("getInfoObject (1) - custom: " + custom + "\n");
		}
		code = ad.getInfos(account, AccountMapper.PASSWORD, AccountMapper.TOKENS, AccountMapper.PWD_SALT);
		if (code == 0){
			throw new RuntimeException("getInfos (2) DID NOT FAIL (as it should have)!");
		}
		code = ad.getInfos(account, AccountMapper.PASSWORD, AccountMapper.ADDRESSES, AccountMapper.LANGUAGE); 	//NOTE: if you query a basic here it will show up in "moreInfo" as well. You don't need to query those! 
		if (code != 0){
			throw new RuntimeException("getInfos (3) FAILED!");
		}else{
			System.out.println("\ngetInfos (3) custom (2): ");
			JSON.printJSONpretty(account.exportJSON());
			System.out.println("------------\n");
		}
		
		//Change password
		JSONObject resetInfo = am.requestPasswordChange(JSON.make("userid", email, "type", IdHandler.Type.email));
		if (!resetInfo.containsKey("token")){
			throw new RuntimeException("requestPasswordChange FAILED!");
		}
		JSON.add(resetInfo, "new_pwd", resetPasswordHash);
		Timer.threadSleep(500);
		if (!am.changePassword(resetInfo)){
			throw new RuntimeException("changePassword FAILED!");
		}
		Timer.threadSleep(500);
		//Authenticate with new pwd 
		tic = Timer.tic();
		request1 = new FakeRequest("GUUID=" + guuid, "PWD=" + resetPassword);
		credentialsV1 = new Credentials(request1);
		if (!am.authenticate(credentialsV1.getAuthJSON())){
			throw new RuntimeException("authenticate with new pwd - username/password - FAILED!");
		}else{
			System.out.println("Authenticated with new pwd - GUUID/PWD - took: " + Timer.toc(tic) + "ms");
		}
		
		//Delete test user
		System.out.println("Test user (presumably) deleted: " + am.deleteUser(JSON.make("userid", guuid)));
				
		System.out.println("\nDONE");
	}
}
