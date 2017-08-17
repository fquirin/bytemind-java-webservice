package de.bytemind.webservice.database;

import org.json.simple.JSONObject;

import de.bytemind.core.databases.DynamoDB;
import de.bytemind.core.tools.Connectors;
import de.bytemind.webservice.users.AccountMapper;

/**
 * Class to setup DynamoDB database tables for different server types.
 * 
 * @author Florian Quirin
 *
 */
public class SetupDynamoDB {
	
	/**
	 * Setup tickets storage for global unique tickets used e.g. in token generation. Throws RuntimeException on fail.
	 */
	public static void setupBasicsTables(){
		String tableName = DB.TICKETS;
		String primaryKey = DynamoDB.PRIMARY_TICKET_KEY;
		String secondaryIndex = "";
		
		JSONObject res = DynamoDB.createSimpleTable(tableName, primaryKey, secondaryIndex);
		if (!Connectors.httpSuccess(res)){
			throw new RuntimeException(SetupDynamoDB.class.getCanonicalName() + " - 'setupBasicsTables()' FAILED! - msg: " + res);
		}
	}
	
	/**
	 * Setup user table to handle user account generation and authentication. Throws RuntimeException on fail.
	 */
	public static void setupAccountsTables(){
		String tableName = DB.USERS;
		String primaryKey = DynamoDB.PRIMARY_USER_KEY;
		String secondaryIndex = AccountMapper.EMAIL;
		
		JSONObject res = DynamoDB.createSimpleTable(tableName, primaryKey, secondaryIndex);
		if (!Connectors.httpSuccess(res)){
			throw new RuntimeException(SetupDynamoDB.class.getCanonicalName() + " - 'setupAccountsTables()' FAILED! - msg: " + res);
		}
	}

}
