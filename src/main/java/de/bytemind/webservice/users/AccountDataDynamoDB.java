package de.bytemind.webservice.users;

import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import de.bytemind.core.databases.DynamoDB;
import de.bytemind.core.server.Statistics;
import de.bytemind.core.tools.Connectors;
import de.bytemind.core.tools.Debugger;
import de.bytemind.core.tools.Is;
import de.bytemind.core.tools.JSON;
import de.bytemind.core.users.Account;
import de.bytemind.webservice.database.DB;

/**
 * AWS DynamoDB database access implementing the Account_Interface.
 * 
 * @author Florian Quirin
 *
 */
public class AccountDataDynamoDB implements AccountData{
	
	//Configuration
	private static String tableName = DB.USERS;
	
	/**
	 * Set DynamoDB table like "Users" etc. ...
	 * @param path - table string created in DynamoDB
	 */
	public void setTable(String path){
		tableName = path;
	}
	
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
	
	//----------Main methods----------

	//get one or more data elements from database
	public int getInfos(Account user, String... keys) {
		
		long tic = System.currentTimeMillis();
		
		//check if the user and api are valid and authorized to access the database
		if (user.getAccessLevel() < 0){
			return 2;
		}
		
		//primaryKey:
		String pK = user.getUserID();
		
		//keys:
		ArrayList<String> checkedKeys = new ArrayList<>();
		for (String s : keys){
			//restrict access to database	TODO: keep this up to date!!! Introduce access levels?
			if (AccountMapper.restrictReadAccess.contains(s.replaceFirst("\\..*", "").trim())){
				//password and tokens retrieval is NOT allowed! NEVER! only if you run this as an authenticator (own class)
				Debugger.println("DB read access to '" + s + "' has been denied!", 3);
				continue;
			}
			checkedKeys.add(s);
		}
				
		//Connect
		JSONObject response = DynamoDB.getItem(tableName, AccountMapper.GUUID, pK, checkedKeys.toArray(new String[]{}));
		//System.out.println("RESPONSE: " + response.toJSONString());			//debug
		//System.out.println("Time needed: " + Debugger.toc(tic) + "ms");		//debug
		
		//Status?
		if (!Connectors.httpSuccess(response)){
			//no access, no connection, wrong search keys or unknown error
			return 4;
		}else{
			if (response.size()==1){
				//best guess: no account found?
				return 3;
			}else{
				//all clear! get the stuff:
				//Item is the main container
				JSONObject item = (JSONObject) response.get("Item");
				//Run through all keys and save them
				for (String k : checkedKeys){
					
					//be sure to USE ONLY CHECKED KEYS HERE!!! 
										
					//we strictly use maps as containers! So we can split strings at "." to get attributes
					String[] levels = k.split("\\.");
					//dig deep if we must
					JSONObject value = DynamoDB.dig(item, levels, 0);
					if (value != null){
						//String type;
						Object found;
						//type conversion and save, DynamoDB keys possible: B,BS,BOOL,L,M,N,NS,S,SS,NULL
						found = DynamoDB.typeConversion(value);
						if (found != null){
							//System.out.println("found: " + found + " - type: " + type);		//debug
							user.setMoreInfo(k, found);
						}
					}
				}
				//save statistics on successful data transfer
				Statistics.addInternalApiHit("AccountData:getInfos", tic);
				
				return 0;
			}
		}
	}
	
	//get object from database - use the bunch read method and directly recover the object
	public Object getInfoObject(Account user, String key) {
		getInfos(user, key);
		return user.getMoreInfo().get(key);
	}

	//set items in database
	@SuppressWarnings("unchecked")
	public int setInfos(Account user, JSONObject data) {
		long tic = System.currentTimeMillis();

		//check consistency of keys and objects
		if (Is.nullOrEmpty(data)){
			return 4;
		}
		
		//check if the user is authorized to access the database
		if (user.getAccessLevel() < 0){
			return 2;
		}

		//primaryKey:
		String pK = user.getUserID();

		//keys:
		ArrayList<String> checkedKeys = new ArrayList<>();
		ArrayList<Object> checkedObjects = new ArrayList<>();
		for (Entry<String, Object> e : (Set<Entry<String, Object>>)data.entrySet()){
			String k = e.getKey();
			Object value = e.getValue();
			String kTop = k.replaceFirst("\\..*", "").trim();

			//restrict access to database 	TODO: keep this up to date! You can also use access levels here ;-)
			if (!AccountMapper.allowWriteAccess.contains(kTop)){
				//password, tokens etc. can NOT be written here! NEVER!
				Debugger.println("DB write access to '" + k + "' has been denied!", 3);
				continue;
			}
			checkedKeys.add(k);
			checkedObjects.add(value);
		}
		if(checkedKeys.isEmpty()){
			return 4;
		}

		//Connect
		int code = DynamoDB.writeAny(tableName, AccountMapper.GUUID, pK, 
				checkedKeys.toArray(new String[]{}), checkedObjects.toArray(new Object[]{}));
		
		if (code != 0){
			Debugger.println("setInfo - DynamoDB error with code: " + code, 1);			//debug
			return code;
		}else{
			//save statistics on successful data transfer
			Statistics.addInternalApiHit("AccountData:setInfos", tic);
			
			return 0;
		}
	}

	//set object in database - use the bunch write method with single key and object
	public int setInfoObject(Account user, String key, Object object) {
		return setInfos(user, JSON.make(key, object));
	}
	
	//---------------------WRITE STATISTICS-------------------------
	
	//write basic statistics like last log-in and total usage
	public boolean writeBasicStatistics(String userID){
		
		long tic = System.currentTimeMillis();
		
		//operation:
		String operation = "UpdateItem";
		
		//add this
		JSONObject expressionAttributeValues = new JSONObject();

		String updateExpressionSet = "ADD ";
		updateExpressionSet += "statistics.totalCalls :val1";	// + ", ";
		JSON.add(expressionAttributeValues, ":val1", DynamoDB.typeConversionDynamoDB(new Integer(1)));
		
		updateExpressionSet += " SET ";
		updateExpressionSet += "statistics.lastLogin = :val2";  // + ", ";
		JSON.add(expressionAttributeValues, ":val2", DynamoDB.typeConversionDynamoDB(String.valueOf(System.currentTimeMillis())));

		//clean up:
		String updateExpression = updateExpressionSet.trim();
		
		//primaryKey:
		JSONObject prime = DynamoDB.getPrimaryUserKey(userID);
		
		//JSON request:
		JSONObject request = new JSONObject();
		JSON.add(request, "TableName", tableName);
		JSON.add(request, "Key", prime);
		JSON.add(request, "UpdateExpression", updateExpression);
		if (!expressionAttributeValues.isEmpty()){
			JSON.add(request, "ExpressionAttributeValues", expressionAttributeValues);
		}
		JSON.add(request, "ReturnValues", "NONE");		//we don't need that info here .. yet
		
		//System.out.println("REQUEST: " + request.toJSONString());		//debug
		
		//Connect
		JSONObject response = DynamoDB.request(operation, request.toJSONString());
		//System.out.println("RESPONSE: " + response.toJSONString());			//debug
		//System.out.println("Time needed: " + Debugger.toc(tic) + "ms");		//debug
		
		if (!Connectors.httpSuccess(response)){
			//errorCode = 3;
			Debugger.println("writeBasicStatistics - DynamoDB Response: " + response.toJSONString(), 1);			//debug
			return false;
		}else{
			//save statistics on successful data transfer
			Statistics.addInternalApiHit("AccountData:writeBasicStatistics", tic);
			
			//errorCode = 0;
			return true;
		}	
	}

}
