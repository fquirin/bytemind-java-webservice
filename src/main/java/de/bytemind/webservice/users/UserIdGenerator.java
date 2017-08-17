package de.bytemind.webservice.users;

import de.bytemind.core.tools.ClassBuilder;
import de.bytemind.core.tools.Security;
import de.bytemind.webservice.server.Config;

/**
 * Generate user IDs, holding a new global ID and a securely hashed password.
 * 
 * @author Florian Quirin
 *
 */
public class UserIdGenerator {
	
	String guuid;
	byte[] saltBytes;
	String salt;
	int iterations;
	String pwd;
	
	private static GlobalIdGenerator guidGenerator;
	public static GlobalIdGenerator getGuidGenerator(){
		if (guidGenerator == null){
			guidGenerator = (GlobalIdGenerator) ClassBuilder.construct(Config.uidGenerator_module);
		}
		return guidGenerator;
	}
	public static void refreshSettings(){
		guidGenerator = (GlobalIdGenerator) ClassBuilder.construct(Config.uidGenerator_module);
	}
	
	/**
	 * Make new ID with unique id.
	 */
	public UserIdGenerator(String pwd) throws Exception{
		guuid = getGuidGenerator().getUserGUID();			//get a new unique ID for the user
		iterations = 20000;
		saltBytes = Security.getRandomSalt(32);
		salt = Security.bytearrayToHexString(saltBytes);
		this.pwd = hashPassword_server(pwd, saltBytes, iterations);
	}
	/**
	 * Make new ID with defined id.
	 */
	public UserIdGenerator(String guuid, String pwd) throws Exception{
		this.guuid = guuid;
		iterations = 20000;
		saltBytes = Security.getRandomSalt(32);
		salt = Security.bytearrayToHexString(saltBytes);
		this.pwd = hashPassword_server(pwd, saltBytes, iterations);
	}
	/**
	 * Rebuild old password.
	 */
	public UserIdGenerator(String pwd, String salt, int N) throws Exception{
		iterations = N;
		saltBytes = Security.hexToByteArray(salt);
		this.pwd = hashPassword_server(pwd, saltBytes, iterations);
	}
	
	public String getGuuid(){
		return guuid;
	}
	public String getPwd(){
		return pwd;
	}
	public String getSalt(){
		return salt;
	}
	public int getIterations(){
		return iterations;
	}

	/**
	 * Hash the submitted password. This is the server-side implementation. If the database is hacked this is the last 
	 * instance to keep the password save as the client side hashing can never be fully hidden.
	 * @param pwd - password to hash
	 * @param salt - random byte-array salt
	 * @param N - hash iterations of implemented algorithm
	 * @return
	 */
	private static String hashPassword_server(String pwd, byte[] salt, int N){
		try {
			return Security.bytearrayToHexString(Security.getEncryptedPassword("galli" + pwd + "who1", salt, N, 32));
		} catch (Exception e) {
			throw new RuntimeException("secure password generation failed! (server)", e);
		}
	}
}
