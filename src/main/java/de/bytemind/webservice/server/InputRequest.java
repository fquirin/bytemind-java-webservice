package de.bytemind.webservice.server;

import de.bytemind.core.client.ClientDefaults;
import de.bytemind.core.statics.Language;
import de.bytemind.core.tools.Is;
import spark.Request;

/**
 * Convenience class to simplify access to "Request" and do some pre-evaluation. 
 * 
 * @author Florian Quirin
 *
 */
public class InputRequest {
	
	Language language = Language.EN;
	String client = ClientDefaults.client_info;
	String timeUNIX;
	String timeLocal;
	String userLocation;
	String environment;
	
	public InputRequest(Request request){
		//get parameters
		
		String language = request.queryParams("lang");
		if (Is.nullOrEmpty(language))	this.language = Language.fromValue(language);
		this.environment = request.queryParams("env");
		this.timeUNIX = request.queryParams("time"); 			//system time - time stamp
		this.timeLocal = request.queryParams("time_local");		//local time date
		String client_info = request.queryParams("client");
		if (Is.nullOrEmpty(client_info))	this.client = client_info;
		this.userLocation = request.queryParams("user_location");
	}
}
