package de.bytemind.webservice.users;

import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONObject;

import de.bytemind.core.users.Account;
import de.bytemind.core.users.Role;

/**
 * A local fake account, only for testing.
 */
public class LocalTestAccount extends Account {

	private final List<String> userRoles = new ArrayList<>();			//user roles managing certain access rights

	private String userId = "uid0815";
	private String language = "en";
	private JSONObject info;
	
	public LocalTestAccount(String userId) {
		super(userId);
		this.userId = userId;
		userRoles.add(Role.user.name()); 		//all users should have this, the rest should be set in JUnit tests
	}

	@Override
	public String getUserID() {
		return userId;
	}
	public void setUserID(String id) {
		userId = id;
	}
	
	@Override
	public String getPreferredLanguage() {
		return language;
	}
	public void setPreferredLanguage(String lang) {
		language = lang;
	}
	
	@Override
	public JSONObject getMoreInfo(){
		return info;
	}
	public void setMoreInfo(JSONObject info){
		this.info = info;
	}

	@Override
	public int getAccessLevel() {
		return 0;
	}
	
	@Override
	public boolean hasRole(String roleName){
		return userRoles != null && userRoles.contains(roleName);
	}
	public void setUserRole(String... roleNames){
		for (String r : roleNames){
			userRoles.add(r);
		}
	}
}
