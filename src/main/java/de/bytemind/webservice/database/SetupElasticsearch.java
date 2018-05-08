package de.bytemind.webservice.database;

import java.io.File;
import java.util.List;

import org.json.simple.JSONObject;

import de.bytemind.core.databases.ElasticSearchConfig;
import de.bytemind.core.databases.Elasticsearch;
import de.bytemind.core.tools.Connectors;
import de.bytemind.core.tools.Debugger;
import de.bytemind.core.tools.FilesAndStreams;
import de.bytemind.core.tools.JSON;
import de.bytemind.core.tools.Timer;
import de.bytemind.webservice.server.Config;

/**
 * Class to setup Elasticsearch indexes for different server types.
 * 
 * @author Florian Quirin
 *
 */
public class SetupElasticsearch {
	
	/**
	 * Clean all Elasticsearch indices.
	 */
	public static void cleanDatabase(){
		JSONObject res = Elasticsearch.customDELETE(ElasticSearchConfig.getEndpoint(),
				"_all");
		if (JSON.getIntegerOrDefault(res, "code", -1) != 0){
			throw new RuntimeException(SetupElasticsearch.class.getCanonicalName() + " - 'cleanDatabase()' FAILED! - msg: " + res);
		}else{
			Timer.threadSleep(1500);
			Debugger.println("Elasticsearch: cleaned all indices! Database is empty now.", 3);
		}
	}
	
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
		}else{
			Debugger.println("Elasticsearch: created index '" + DB.ES_GUID + "'", 3);
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
		}else{
			Debugger.println("Elasticsearch: created index '" + DB.WHITELIST + "'", 3);
		}
	}
	
	/**
	 * Setup mapping for custom user indices loaded from '[Settings]/Elasticsearch/customMappings/' folder.
	 */
	public static void setupCustomMappings(){
		List<File> files = FilesAndStreams.directoryToFileList(Config.configurationFolder + "Elasticsearch/customMappings/", null, false);
		//Write mappings
		for (File f : files){
			String index = f.getName().replaceFirst("\\.json$", "").trim();
			JSONObject mapping = JSON.readJsonFromFile(f.getAbsolutePath());
			JSONObject res = Elasticsearch.putMapping(ElasticSearchConfig.getEndpoint(), 
					index, mapping);
			if (JSON.getIntegerOrDefault(res, "code", -1) != 0){
				throw new RuntimeException(SetupElasticsearch.class.getCanonicalName() + " - 'setupCustomMappings()' FAILED! - msg: " + res);
			}else{
				Debugger.println("Elasticsearch: created index '" + index + "'", 3);
			}
		}
	}

}
