package de.bytemind.webservice.users;

import org.json.simple.JSONObject;

import de.bytemind.core.users.Account;

/**
 * Interface for any account management class.
 * 
 * @author Florian Quirin
 *
 */
public interface AccountData {
	
	/**
	 * Test if module works.
	 * @return works=true 
	 */
	public boolean testModule();
	
	/**
	 * Get a bunch of user values from account database at the same time to reduce traffic and write them to user variable.
	 * If a key is not found the value remains null.
	 * The database implementation has to check if the user is authenticated correctly (and not just a user created with an ID and access level)! 
	 * 
	 * @param user - the user we are looking for and the variable we modify
	 * @param keys - string array of keys that describe database entries
	 * 
	 * @return error code (0 - no error, 1 - can't reach database, 2 - access denied (or invalid parameters), 3 - no account found, 4 - other error (e.g. wrong key combination)
	 */
	public int getInfos(Account user, String... keys);
	
	/**
	 * Set a bunch of user values in account database at the same time to reduce traffic. 
	 * If a key is not found the value remains null.
	 * The database implementation has to check if the user is authenticated correctly (and not just a user created with an ID and access level)!
	 * 
	 * @param user - the user we are looking for
	 * @param data - JSON object with data to write. Valid objects inside the JSON are: Strings, numbers, HashMap, ArrayList, JSONArray and Boolean
	 * 
	 * @return error code (0 - no error, 1 - can't reach database, 2 - access denied, 3 - no account found, 4 - other error (e.g. wrong key combination))
	 */
	public int setInfos(Account user, JSONObject data); 
	
	/**
	 * Get any user specific object from account database.
	 * The database implementation has to check if the user is authenticated correctly (and not just a user created with an ID and access level)!<br>
	 * NOTE: If "key" is one of the account's basics (like language or name) then this method might give null and you have to check the basics first.
	 * 
	 * @param user - get info about this user
	 * @param key - the key of the database entry we are looking for
	 * 
	 * @return info object or null if it is not found
	 */
	public Object getInfoObject(Account user, String key);
	
	/**
	 * Set any user specific object in account database.
	 * The database implementation has to check if the user is authenticated correctly (and not just a user created with an ID and access level)!
	 * 
	 * @param user - set info about this user
	 * @param key - the key of the database entry we want to change
	 * @param object - the object to add to the database. NOTE: Valid objects are: Strings, numbers, HashMap, ArrayList, JSONArray and Boolean
	 * 
	 * @return error code (0 - no error, 1 - can't reach database, 2 - access denied, 3 - no account found, 4 - other error)
	 */
	public int setInfoObject(Account user, String key, Object object);
	
	/**
	 * Write basic statistics for a user like last log-in and total usage etc. ...
	 * @param userID - user to track
	 * @return
	 */
	public boolean writeBasicStatistics(String userID);

}
