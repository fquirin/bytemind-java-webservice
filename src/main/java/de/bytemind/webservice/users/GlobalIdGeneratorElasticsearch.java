package de.bytemind.webservice.users;

import java.util.HashMap;

import org.json.simple.JSONObject;

import de.bytemind.core.databases.ElasticSearchConfig;
import de.bytemind.core.tools.Connectors;
import de.bytemind.core.tools.JSON;
import de.bytemind.core.users.IdHandler;
import de.bytemind.webservice.database.DB;

/**
 * Class to generate global unique IDs with Elasticsearch.
 * 
 * @author Florian Quirin
 *
 */
public class GlobalIdGeneratorElasticsearch implements GlobalIdGenerator{
	
	//offset
	private long guidOffset = 997; 		//IDs should have at least 4 digits
	
	/**
	 * Get a global unique ID for new users.
	 * @throws RuntimeException
	 */
	public String getUserGUID() throws RuntimeException{
		long guid = makeElasticGUID(guidOffset);
		return IdHandler.user_id_prefix + (guidOffset + guid);
	}
	
	/**
	 * Get a global unique ID for general purpose.
	 * @throws RuntimeException
	 */
	public String getTicketGUID() throws RuntimeException{
		long guid = makeElasticGUID(guidOffset);
		return "t" + (guidOffset + guid);
	}
	
	/**
	 * Return the GUID that has been last issued by this server.
	 */
	public long getLastIssuedGUID(){
		return getLastIssuedElasticGUID();
	}
	
	//------------Implementations----------------
	
	//last issued IDs
	private static long lastElasticGUID = -1;	//this is not a reliable way to track the GUID but it gives a rough idea where we are
	
	/**
	 * Uses Elasticsearch to generate a GUID. 
	 */
	private static long makeElasticGUID(long guidOffset){
		//build URL
		String url = ElasticSearchConfig.getEndpoint() + "/" + DB.ES_GUID + "/" + "sequence" + "/" + "ticket" + "/_update";
		
		//build data
		JSONObject data = new JSONObject();
			JSONObject doc = new JSONObject();
			JSON.add(doc, "near_id", lastElasticGUID + 1l); 	//note: this is only a rough indication
			JSON.add(doc, "offset", guidOffset);
		JSON.add(data, "doc", doc);
		JSON.add(data, "detect_noop", false);
		
		//make update POST
		HashMap<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Content-Length", Integer.toString(data.toJSONString().getBytes().length));
		
		JSONObject result = Connectors.httpPOST(url, data.toJSONString(), headers);
		//System.out.println(result.toJSONString()); 		//debug
		
		//success?
		try{
			long version = (long) result.get("_version");
			long shards_success = (long) JSON.getJObject(result, "_shards").get("successful");
			if (shards_success == 1){
				lastElasticGUID = version;
				return version;
			}else{
				throw new RuntimeException(GlobalIdGeneratorElasticsearch.class.getCanonicalName() + " - ES reports fail in shard check!");
			}

		//error
		}catch (Exception ex){
			String error = GlobalIdGeneratorElasticsearch.class.getCanonicalName() + " - ES failed to generate GUID!";
			//System.err.println(DateTime.getLogDate() + " WARNING - " + error);
			throw new RuntimeException(error, ex);
		}
	}
	
	/**
	 * Get approximate last issued ID. This is not a reliable way to track the GUID but it gives a rough idea where we are.
	 */
	private static long getLastIssuedElasticGUID(){
		return lastElasticGUID;
	}

}
