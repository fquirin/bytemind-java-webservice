package de.bytemind.webservice.users;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.bytemind.core.users.AccountBasicInfo;

/**
 * This class handles the mapping of certain account entries to the correct database and fields (if they are nestet for example)
 * 
 * @author Florian Quirin
 *
 */
public class AccountMapper {
	
	//Statics - many names correspond directly to User Class naming
	
	//--topics--
	public static final String GUUID = "Guuid";			//global unique user id 
	public static final String EMAIL = AccountBasicInfo.EMAIL;			//email aka second search key
	public static final String PHONE = AccountBasicInfo.PHONE;			//phone number aka to be third search key
	public static final String PASSWORD = "pwd";		//hashed password
	public static final String PWD_SALT = "pwd_salt";				//salt for hashed password
	public static final String PWD_ITERATIONS = "pwd_iteration";	//iterations for hashed password
	public static final String TOKENS = "tokens";		//different tokens for security purposes

	public static final String USER_NAME = AccountBasicInfo.NAME;		//user name as seen in User class: "<nickname>Boss<firstname>...<lastname>...";
	public static final String ROLES = AccountBasicInfo.ROLES;			//user roles like "user", "developer", "tester", ...
	public static final String LANGUAGE = AccountBasicInfo.LANGUAGE;	//preferred language as 2-letter ISO code
	
	public static final String STATISTICS = "statistics";	//collect user statistics
	
	public static final String ADDRESSES = "addresses";	//all addresses of the user like home, work, pois etc.
	public static final String INFOS = "infos";			//collect info of all kinds of stuff
	
	//--subs--
	
	//names
	public static final String USER_NAME_FIRST = USER_NAME + ".first";		//first name
	public static final String USER_NAME_LAST = USER_NAME + ".last";		//last name
	public static final String USER_NAME_NICK = USER_NAME + ".nick";		//nick name
	
	//infos
	public static final String USER_BIRTH = INFOS + ".birth";				//birthday - CAREFUL! Its hard coded inside account load basics
	public static final String USER_GENDER = INFOS + ".gender";				//gender - not (yet?) in basics
	
	//------Collections to handle write access-------
	
	//keep this list up to date to handle all regular write requests
	public static final List<String> allowWriteAccess = Arrays.asList(
			USER_NAME, LANGUAGE, INFOS, ADDRESSES		
	);
	//these here MUST be restricted at any cost and can only be written by secure server methods
	/*
	public static final List<String> restrictWriteAccess = Arrays.asList(
			GUUID, EMAIL, PHONE, PASSWORD, PWD_SALT, PWD_ITERATIONS, TOKENS, ROLES, STATISTICS
	);
	*/
	//these here MUST be restricted at any cost and can only be read by secure server methods
	public static final List<String> restrictReadAccess = Arrays.asList(
			PASSWORD, PWD_SALT, PWD_ITERATIONS, TOKENS
	);
	
	//------------------- ACCOUNT BASICS ---------------------
	//define what is set during account creation and what is always loaded on authentication
	
	//write this to user account when its first created in addition to the basics: PASSWORD, PWD_SALT, PWD_ITERATIONS, ...
	public static Map<String, Object> addCreationBasics = new HashMap<>();
	static {
		addCreationBasics.put(ADDRESSES, new HashMap<String, Object>()); 	//empty Map waiting to be filled
	}

	//basics to read, in addition these are always loaded:	AccountMapper.GUUID, AccountMapper.PASSWORD, AccountMapper.PWD_SALT, 
	//AccountMapper.PWD_ITERATIONS, AccountMapper.TOKENS, AccountMapper.ROLES
	public static String[] addReadBasics = new String[]{
			EMAIL, PHONE, USER_NAME, LANGUAGE, USER_BIRTH
	};
	//TODO: Needs simplification! 
	//NOTE: if you add more basics here you might want to adjust "Account" and "AccountBasicInfo" as well. 
}
