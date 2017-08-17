package de.bytemind.webservice.server;

import org.json.simple.JSONObject;

import de.bytemind.core.client.ClientDefaults;
import de.bytemind.core.tools.Is;
import de.bytemind.core.tools.JSON;
import de.bytemind.core.tools.Security;
import de.bytemind.core.users.IdHandler;
import spark.Request;

/**
 * Small class to get and hold credentials of server request. 
 * Converts request with all kinds of different types of authentication options (id, email, token, username-password)
 * to one common credentials-set.
 *  
 * @author Florian Quirin
 *
 */
public class Credentials {
	
	String username;
	String password;
	String idType;
	String client;
	
	String key;
	
	public Credentials(Request request){
		String key = request.queryParams("KEY");
		if (Is.nullOrEmpty(key)){
			String guuid = request.queryParams("GUUID");
			String pwd = request.queryParams("PWD");
			if (Is.notNullOrEmpty(guuid) && Is.notNullOrEmpty(pwd)){
				key = guuid + ";" + Security.hashPassword_client(pwd);
			} 
		}
		client = request.queryParams("client");
		if (Is.nullOrEmpty(client)){
			client = ClientDefaults.client_info;
		}
		if (Is.notNullOrEmpty(key)){
			this.key = key;
			String[] info = key.split(";",2);
			if (info.length == 2){
				this.username = info[0].toLowerCase();
				this.password = info[1];
				//password must be 64 or 65 char hashed version - THE CLIENT IS EXPECTED TO DO THAT!
				//65 char is the temporary token
				if ((password.length() == 64) || (password.length() == 65)){
					idType = IdHandler.autodetectType(username);
				}
			}
		}
	}
	
	/**
	 * Get credentials as JSON, compatible to "authentication" method.
	 */
	public JSONObject getAuthJSON(){
		return JSON.make("userId", username, "pwd", password, "idType", idType, "client", client);
	}
	
	public String getUserName(){
		return username;
	}
	public String getPassword(){
		return password;
	}
	public String getIdType(){
		return idType;
	}
	public String getClient(){
		return client;
	}

}
