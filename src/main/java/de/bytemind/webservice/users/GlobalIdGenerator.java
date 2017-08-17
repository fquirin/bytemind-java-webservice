package de.bytemind.webservice.users;

/**
 * Interface for classes that can generate unique global IDs where "global" really means across the whole framework usually database supported.
 * 
 * @author Florian Quirin
 *
 */
public interface GlobalIdGenerator {
	
	/**
	 * Get a global unique ID for new users.
	 * @throws RuntimeException
	 */
	public String getUserGUID() throws RuntimeException;
	
	/**
	 * Get a global unique ID for general purpose.
	 * @throws RuntimeException
	 */
	public String getTicketGUID() throws RuntimeException;
	
	/**
	 * Return the GUID that has been last issued by this server.
	 */
	public long getLastIssuedGUID();

}
