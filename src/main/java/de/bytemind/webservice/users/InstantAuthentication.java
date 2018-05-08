package de.bytemind.webservice.users;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;

import de.bytemind.core.tools.Converters;
import de.bytemind.core.tools.JSON;
import de.bytemind.core.tools.Security;
import de.bytemind.core.users.AccountBasicInfo;
import de.bytemind.core.users.Authentication;
import de.bytemind.core.users.Role;
import de.bytemind.webservice.server.Config;

/**
 * Basically skip authentication and allow all users. Only user that is handled in a special way and checked for password is the admin.
 * 
 * @author Florian Quirin
 *
 */
public class InstantAuthentication implements Authentication {

	String userid = "";
	String client = "";
	private boolean isAdmin = false;
	
	private HashMap<String, Object> rawBasicInfo = new HashMap<>();
	
	
	@Override
	public void setRequestInfo(Object request) {}

	@Override
	public boolean authenticate(JSONObject info) {
		
		userid = (String) info.get("userId");
		client = (String) info.get("client");
		String password = (String) info.get("pwd");
		
		if (userid != null && userid.equals(Config.superuserId)){
			String pwdHash = Security.hashPassword_client(password);
			if (Config.isSuperuserPwdHash(pwdHash) || Config.isSuperuserPwdHash(password)){
				isAdmin = true;
				return true;
			}else{
				isAdmin = false;
				return false;
			}
		}
		isAdmin = false;
		return true;
	}

	@Override
	public boolean logout(String userid, String client) {
		return true;
	}

	@Override
	public boolean logoutAllClients(String userid) {
		return true;
	}

	@Override
	public String getUserID() {
		return userid;
	}

	@Override
	public int getAccessLevel() {
		return 0;
	}

	@Override
	public Map<String, Object> getRawBasicInfo() {
		return rawBasicInfo;
	}

	@Override
	public AccountBasicInfo upgradeBasicInfo(Map<String, Object> rawBasicInfo) {
		JSONObject basicInfoJson = new JSONObject();
		JSON.put(basicInfoJson, AccountMapper.GUUID, userid);
		if (isAdmin){
			JSON.put(basicInfoJson, AccountMapper.ROLES, Converters.list2JsonArray(Arrays.asList(Role.user.name())));
		}else{
			//TODO: superuser should have all roles
			JSON.put(basicInfoJson, AccountMapper.ROLES, Converters.list2JsonArray(Arrays.asList(Role.user.name(), Role.developer.name(), Role.tester.name(), Role.superuser.name())));
		}
		AccountBasicInfo basicInfo = new AccountBasicInfo(basicInfoJson);
		return basicInfo;
	}

	@Override
	public int getErrorCode() {
		// TODO Auto-generated method stub
		return 0;
	}

}
