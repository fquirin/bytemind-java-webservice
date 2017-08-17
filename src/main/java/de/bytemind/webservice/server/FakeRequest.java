package de.bytemind.webservice.server;

import java.util.HashMap;
import java.util.Map;

import de.bytemind.core.users.Account;
import de.bytemind.core.users.IdHandler;
import de.bytemind.webservice.users.LocalTestAccount;
import spark.Request;

/**
 * Spark fake 'Request' to construct your own request for testing or anything else.
 * 
 * @author Florian Quirin, Daniel Naber
 *
 */
public class FakeRequest extends Request {
	private final Map<String, String> params = new HashMap<>();
	private Account fakeAccount;

	/**
	 * Create a fake request with parameters like "lang=de", "GUUID=uid1002", ...<br>
	 * Automatically sets a LocalTestAccount as well (if this is a problem for your test use "setAccount" after constructor).
	 */
	public FakeRequest(String... params) {
		for (String param : params) {
			String[] parts = param.split("=",2);
			this.params.put(parts[0], parts[1]);
		}
		fakeAccount = new LocalTestAccount(IdHandler.user_id_prefix + "0815");
	}
	public FakeRequest(Account fakeAccount, String... params) {
		this(params);
		this.fakeAccount = fakeAccount;
	}
	
	/**
	 * Set a new account (e.g. LocalTestAccount) as this request's account.<br>
	 * Note: use attribute(Defaults.ACCOUNT_ATTR) to get account back.
	 */
	public void setAccount(Account newAccount){
		fakeAccount = newAccount;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T attribute(String attribute) {
		if (attribute.equals(ByteMindServer.ACCOUNT_ATTR)) {
			return (T) fakeAccount;
		}
		return super.attribute(attribute);
	}

	@Override
	public String headers(String header) {
		if (header.equalsIgnoreCase("Content-type")) {
			return "application/json";
		}
		return super.headers(header);
	}
	
	@Override
	public String contentType() {
		return "application/json";
	}

	@Override
	public String queryParams(String queryParam) {
		return params.get(queryParam);
	}

	@Override
	public String[] queryParamsValues(String queryParam) {
		return new String[]{params.get(queryParam)};
	}
}
