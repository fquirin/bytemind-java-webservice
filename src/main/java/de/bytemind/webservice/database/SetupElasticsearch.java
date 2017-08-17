package de.bytemind.webservice.database;

import org.json.simple.JSONObject;

import de.bytemind.core.databases.ElasticSearchConfig;
import de.bytemind.core.databases.Elasticsearch;
import de.bytemind.core.tools.Connectors;
import de.bytemind.core.tools.JSON;
import de.bytemind.webservice.server.Config;

/**
 * Class to setup Elasticsearch indexes for different server types.
 * 
 * @author Florian Quirin
 *
 */
public class SetupElasticsearch {
	
	/**
	 * Setup mapping for the global unique id generator. Throws RuntimeException on fail.
	 */
	public static void setupGuidGenerationMapping(){
		JSONObject res = Elasticsearch.putMapping(ElasticSearchConfig.getEndpoint(), 
				DB.ES_GUID, JSON.readJsonFromFile(Config.configurationFolder + "Elasticsearch/guid-mapping.json"));
		if (JSON.getIntegerOrDefault(res, "code", -1) != 0){
			throw new RuntimeException(SetupElasticsearch.class.getCanonicalName() + " - 'setupGuidGenerationMapping()' FAILED! - msg: " + res);
		}
		//SET FIRST ENTRY so that _update works later
		res = Elasticsearch.customPUT(ElasticSearchConfig.getEndpoint(), DB.ES_GUID + "/sequence/ticket", JSON.make("near_id", 0, "offset", 0));
		if (!Connectors.httpSuccess(res)){
			throw new RuntimeException(SetupElasticsearch.class.getCanonicalName() + " - 'setupGuidGenerationMapping()' FAILED! - msg: " + res);
		}
	}
	
	/**
	 * Setup mapping for the user authentication white-list. Throws RuntimeException on fail.
	 */
	public static void setupAuthenticationWhitelistMapping(){
		JSONObject res = Elasticsearch.putMapping(ElasticSearchConfig.getEndpoint(), 
				DB.WHITELIST, JSON.readJsonFromFile(Config.configurationFolder + "Elasticsearch/whitelist-mapping.json"));
		if (JSON.getIntegerOrDefault(res, "code", -1) != 0){
			throw new RuntimeException(SetupElasticsearch.class.getCanonicalName() + " - 'setupAuthenticationWhitelistMapping()' FAILED! - msg: " + res);
		}
	}

}
