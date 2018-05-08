package de.bytemind.webservice.database;

import org.json.simple.JSONObject;

import de.bytemind.core.client.ClientDefaults;
import de.bytemind.core.databases.KnowledgeDatabase;
import de.bytemind.core.server.Statistics;
import de.bytemind.core.tools.ClassBuilder;
import de.bytemind.core.tools.Converters;
import de.bytemind.core.tools.Debugger;
import de.bytemind.core.tools.JSON;
import de.bytemind.core.users.Account;
import de.bytemind.core.users.IdHandler;
import de.bytemind.webservice.server.Config;
import de.bytemind.webservice.users.AccountData;

/**
 * Top-level database methods.
 * 
 * @author Florian Quirin
 *
 */
public class DB {
	
	//statics
	//TODO: update indices (storage, knowledge used? new ones?)
	public static final String USERS = "users";				//user data like account, list, contacts, etc.
	public static final String TICKETS = "tickets";			//tickets (unique IDs) that can be used for registration tokens or actions that need to be secured 
	public static final String STORAGE = "storage";			//unsorted data for later processing
	public static final String KNOWLEDGE = "knowledge";		//processed and sorted data for queries
	public static final String USERDATA = "userdata";		//all kinds of user data entries like cmd-mapping, lists, alarms, ... - Type: services, alarms, lists, ..., ID: userID
	public static final String WHITELIST = "whitelist";		//white-lists of e.g. users
	public static final String ES_GUID = "guid";			//global unique id generator for Elasticsearch (an index that generates IDs by updating an entry and using the version number) 
	
	//----------Database Implementations----------

	private static AccountData accountsDB;			//BASIC USER ACCOUNT STUFF
	//note: you can use Account_DynamoDB.setTable(...) to access other tables in DynamoDB
	private static KnowledgeDatabase knowledgeDB;	//BASICALLY EVERYTHING ELSE
	
	public static AccountData getAccountsDB(){
		if (accountsDB == null){
			accountsDB = (AccountData) ClassBuilder.construct(Config.accountData_module);
		}
		return accountsDB;
	}
	public static KnowledgeDatabase getKnowledgeDB(){
		if (knowledgeDB == null){
			knowledgeDB = (KnowledgeDatabase) ClassBuilder.construct(Config.knowledgeDB_module);
		}
		return knowledgeDB;
	}
	/**
	 * Refresh the settings for accounts and knowledge database.
	 */
	public static void refreshSettings(){
		accountsDB = (AccountData) ClassBuilder.construct(Config.accountData_module);
		knowledgeDB = (KnowledgeDatabase) ClassBuilder.construct(Config.knowledgeDB_module);
	}
	
	
	//TODO: remove all following methods (except the ones at the bottom maybe) and add them to database type classes like:
	//account, storage, knowledge, etc.
	//In each of these we might need: set, setAsync, get, getAsync, update, updateAsync, delete, deleteAsync.
	//Data should always be in JSON format, set should overwrite the whole object at top of hierarchy, update should only change the item at the bottom of the hierarchy
	
	
	//----------Account methods----------
	
	//TODO: add key filters and layers of security!! Best thing would be to restrict this to a user defined list of available info
	//TODO: we need this e.g. to transfer data from one account to another (see banking), but it needs to be less open.
	
	//GET
	/**
	 * Get account info for id (basically this means: get complete account class as JSON filled with "values" to "keys" if available).
	 * @param userId - user id
	 * @param keys - account info to load
	 * @return JSONObject with basic account info and "moreInfo" if available or null
	 */
	public static JSONObject getAccountInfos(String userId, String... keys) {
		//create superuser
		Account user = createSuperuser(userId);
		//get info
		int res_code = getAccountsDB().getInfos(user, keys);
		if (res_code == 0){
			return user.exportJSON();
		}else{
			return null;
		}
	}
	/**
	 * Get object from account by using id.
	 * @param userId - user id
	 * @param key - account info to load
	 * @return object loaded from account
	 */
	public static Object getAccountObject(String userId, String key) {
		//create superuser
		Account user = createSuperuser(userId);
		return getAccountsDB().getInfoObject(user, key);
	}
	
	//SET
	/**
	 * Set an account info of user id. 
	 * @param userId - user id
	 * @param data - JSON object with data to write. Valid objects inside the JSON are: Strings, numbers, Map, List and Boolean
	 * @return error code (0 - no error, 1 - can't reach database, 2 - access denied, 3 - no account found, 4 - other error (e.g. wrong key combination))
	 */
	public static int setAccountInfos(String userId, JSONObject data) {
		//create superuser
		Account user = createSuperuser(userId);
		return getAccountsDB().setInfos(user, data);
	}
	/**
	 * Set an account info of user id. 
	 * @param userId - user id
	 * @param key - keys to set
	 * @param object - values to keys
	 * @return error code (0 - no error, 1 - can't reach database, 2 - access denied, 3 - no account found, 4 - other error (e.g. wrong key combination))
	 */
	public static int setAccountInfoObject(String userId, String key, Object object) {
		//create superuser
		Account user = createSuperuser(userId);
		return getAccountsDB().setInfoObject(user, key, object);
	}
	
	//------------Knowledge--------------
	
	//TODO: implement a basic request for user data stored in knowledge db!
	
	/**
	 * Get one or all lists of the user with a certain type and optionally title.
	 * @return null if there was an error, else a list (that can be empty)
	 */
	/*
	public static List<UserDataList> getListData(String userId, Section section, String indexType, HashMap<String, Object> filters){
		long tic = Debugger.tic();
		//validate
		if (indexType != null && !indexType.isEmpty() && indexType.equals(IndexType.unknown.name())){
			//TODO: think about that again
			indexType = "";
		}
		String title = (filters.containsKey("title"))? (String) filters.get("title") : "";
		if (userId.isEmpty() || (indexType.isEmpty() && title.isEmpty())){
			throw new RuntimeException("getListData - userId or (indexType and title) invalid!");
		}
		if (userId.contains(",")){
			throw new RuntimeException("getListData - MULTIPLE userIds are not (yet) supported!");
		}
		if (section == null || section.name().isEmpty()){
			throw new RuntimeException("getListData - section is missing!");
		}
		String sectionName = section.name();

		List<QueryElement> matches = new ArrayList<>(); 
		matches.add(new QueryElement("user", userId));
		if (!sectionName.equals("all")) matches.add(new QueryElement("section", sectionName));
		if (!indexType.isEmpty()){
			matches.add(new QueryElement("indexType", indexType));
		}
		if (!title.isEmpty()){
			matches.add(new QueryElement("title", filters.get("title"), ""));
		}
		String query = EsQueryBuilder.getBoolMustMatch(matches).toJSONString();
		//System.out.println("query: " + query);
		
		JSONObject data = new JSONObject();
		data = knowledge.searchByJson(USERDATA + "/" + UserDataList.LISTS_TYPE, query);
		
		List<UserDataList> lists = null;
		if (Connectors.httpSuccess(data)){
			JSONArray listsArray = JSON.getJArray(data, new String[]{"hits", "hits"});
			lists = new ArrayList<>();
			if (listsArray != null){
				for (Object o : listsArray){
					JSONObject jo = (JSONObject) o;
					if (jo.containsKey("_source")){
						lists.add(new UserDataList((JSONObject) jo.get("_source"), (String) jo.get("_id")));
					}
				}
			}
		}
		
		//statistics
		Statistics.addInternalApiHit("getListDataFromDB", tic);
		      	
		return lists;
	}
	*/
	/**
	 * Delete one or all lists of the user with certain conditions.
	 * @return null if there was an error, else a list (that can be empty)
	 */
	/*
	public static long deleteListData(String userId, String docId, HashMap<String, Object> filters){
		long tic = Debugger.tic();
		//TODO: support delete by index and title?
		//String title = (filters.containsKey("title"))? (String) filters.get("title") : "";
		//String indexType = (filters.containsKey("indexType"))? (String) filters.get("indexType") : "";
		if (userId.isEmpty()){
			throw new RuntimeException("deleteListData - userId missing or invalid!");
		}
		if (userId.contains(",")){
			throw new RuntimeException("deleteListData - MULTIPLE userIds are not (yet) supported!");
		}
		if (docId == null || docId.isEmpty()){
			throw new RuntimeException("deleteListData - document id is missing!");
		}

		List<QueryElement> matches = new ArrayList<>(); 
		matches.add(new QueryElement("user", userId));
		matches.add(new QueryElement("_id", docId));
		String query = EsQueryBuilder.getBoolMustMatch(matches).toJSONString();
		//System.out.println("query: " + query);
		
		JSONObject data = new JSONObject();
		data = knowledge.deleteByJson(USERDATA + "/" + UserDataList.LISTS_TYPE, query);

		long deletedObjects = -1;
		if (Connectors.httpSuccess(data)){
			Object o = data.get("deleted");
			if (o != null){
				deletedObjects = (long) o;
			}
		}
		
		//statistics
		Statistics.addInternalApiHit("deleteListDataFromDB", tic);
		      	
		return deletedObjects;
	}
	*/
	/**
	 * Set a user data list by either overwriting the doc at 'docId' or creating a new one.
	 * @return JSONObject with "code" and optionally "_id" if the doc was newly created
	 */
	/*
	public static JSONObject setListData(String docId, String userId, Section section, String indexType, JSONObject listData){
		long tic = Debugger.tic();
		if (userId.isEmpty() || indexType.isEmpty()){
			throw new RuntimeException("setListData - 'userId' or 'indexType' invalid!");
		}
		//safety overwrite
		JSON.put(listData, "user", userId);
		JSON.put(listData, "section", section.name());
		JSON.put(listData, "indexType", indexType);
		
		//Note: if the 'title' is empty this might unintentionally overwrite a list or create a new one
		String title = (String) listData.get("title");
		if ((docId == null || docId.isEmpty()) && (title == null || title.isEmpty())){
			throw new RuntimeException("setUserDataList - 'title' AND 'id' is MISSING! Need at least one.");
		}
		if (section == null || section.name().isEmpty()){
			throw new RuntimeException("setUserDataList - 'section' is MISSING!");
		}
		
		//simply write when no docId is given
		JSONObject setResult;
		if (docId == null || docId.isEmpty()){
			JSON.put(listData, "lastEdit", System.currentTimeMillis());
			setResult = knowledge.setAnyItemData(DB.USERDATA, UserDataList.LISTS_TYPE, listData);
		
		}else{
			listData.remove("_id"); //prevent to have id twice
			JSON.put(listData, "lastEdit", System.currentTimeMillis());
			JSONObject newListData = new JSONObject();
			//double-check if someone tampered with the docID by checking userID via script
			String dataAssign = "";
			for(Object keyObj : listData.keySet()){
				String key = (String) keyObj;
				dataAssign += ("ctx._source." + key + "=params." + key + "; ");
			}
			JSONObject script = JSON.make("lang", "painless",
					"inline", "if (ctx._source.user != params.user) { ctx.op = 'noop'} " + dataAssign.trim(),
					"params", listData);
			JSON.put(newListData, "script", script);//"ctx.op = ctx._source.user == " + userId + "? 'update' : 'none'");
			JSON.put(newListData, "scripted_upsert", true);
			
			int code = knowledge.updateItemData(DB.USERDATA, UserDataList.LISTS_TYPE, docId, newListData);
			setResult = JSON.make("code", code);
		}
		
		//statistics
		Statistics.addInternalApiHit("setListDataInDB", tic);
				
		return setResult;
	}
	*/
	
	//TODO: rewrite the asynchronous write methods to collect data and write all at the same time as batchWrite when finished collecting
	
	/**
	 * Save stuff to database without waiting for reply, making this save method UNSAVE so keep that in mind when using it.
	 * Errors get written to log.
	 * @param index - index or table name like e.g. "account" or "knowledge"
	 * @param type - subclass name, e.g. "user", "lists", "banking" (for account) or "geodata" and "dictionary" (for knowledge)
	 * @param item_id - unique item/id name, e.g. user email address, dictionary word or geodata location name
	 * @param data - JSON string with data objects that should be stored for index/type/item, e.g. {"name":"john"}
	 */
	public static void saveKnowledgeAsync(String index, String type, String item_id, JSONObject data){
		Thread thread = new Thread(){
		    public void run(){
		    	//time
		    	long tic = Debugger.tic();
		    	
		    	JSONObject res = getKnowledgeDB().setItemData(index, type, item_id, data);
		    	int code = JSON.getIntegerOrDefault(res, "code", -1);
				if (code != 0){
					Debugger.println("KNOWLEDGE DB ERROR! - PATH: " + index + "/" + type + "/" + item_id + " - TIME: " + System.currentTimeMillis(), 1);
				}else{
					//Debugger.println("KNOWLEDGE DB UPDATED! - PATH: " + index + "/" + type + "/" + item_id + " - TIME: " + System.currentTimeMillis(), 1);
					Statistics.addInternalApiHit("saveKnowledgeAsync", tic);
				}
		    }
		};
		thread.start();
	}
	/**
	 * Save stuff to database without waiting for reply, making this save method UNSAVE so keep that in mind when using it.
	 * Errors get written to log. This method does not require an ID, it is auto-generated.
	 * @param index - index or table name like e.g. "account" or "knowledge"
	 * @param type - subclass name, e.g. "user", "lists", "banking" (for account) or "geodata" and "dictionary" (for knowledge)
	 * @param data - JSON string with data objects that should be stored for index/type/item, e.g. {"name":"john"}
	 */
	public static void saveKnowledgeAsyncAnyID(String index, String type, JSONObject data){
		Thread thread = new Thread(){
		    public void run(){
		    	//time
		    	long tic = Debugger.tic();
		    	
		    	int code = JSON.getIntegerOrDefault(getKnowledgeDB().setAnyItemData(index, type, data), "code", -1);
				if (code != 0){
					Debugger.println("KNOWLEDGE DB ERROR! - PATH: " + index + "/" + type + "/[rnd] - TIME: " + System.currentTimeMillis(), 1);
				}else{
					//Debugger.println("KNOWLEDGE DB UPDATED! - PATH: " + index + "/" + type + "/[rnd] - TIME: " + System.currentTimeMillis(), 1);
					Statistics.addInternalApiHit("saveKnowledgeAsyncAnyID", tic);
				}
		    }
		};
		thread.start();
	}
	
	/**
	 * Add a user to the white-list.
	 */
	public static int saveWhitelistUserEmail(String email){
		//System.out.println("save whitelist user attempt - email: " + email); 		//debug
		if (email == null || email.isEmpty()){
			return -1;
		}
		
		JSONObject data = new JSONObject();
		JSON.add(data, "uid", IdHandler.clean(email));
		JSON.add(data, "info", "-");
		
		int code = JSON.getIntegerOrDefault(getKnowledgeDB().setAnyItemData(WHITELIST, "users", data), "code", -1);
		//System.out.println("save whitelist user result - code: " + code); 		//debug
		return code;
	}
	/**
	 * Search a user on the white-list.
	 */
	public static boolean searchWhitelistUserEmail(String email){
		if (email == null || email.isEmpty()){
			return false;
		}
		JSONObject data = getKnowledgeDB().searchSimple(WHITELIST + "/" + "users", "uid:" + IdHandler.clean(email));
		//System.out.println("whitelist user search: " + data.toJSONString());
		try{
			int hits = Converters.obj2int(((JSONObject) data.get("hits")).get("total"), -1);
			if (hits > 0){
				return true;
			}else{
				return false;
			}
		}catch (Exception e){
			Debugger.println("Whitelist search failed! ID: " + email + " - error: " + e.getMessage(), 1);
			return false;
		}
	}
		
	//--------------Tools----------------
	
	/**
	 * Create super user for account access.
	 * @param userId - unique user id to access
	 */
	private static Account createSuperuser(String userId){
		return new Account(userId, 0, ClientDefaults.client_info);
	}
}
