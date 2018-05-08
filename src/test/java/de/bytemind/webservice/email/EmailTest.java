package de.bytemind.webservice.email;

import de.bytemind.core.statics.Language;
import de.bytemind.core.tools.ClassBuilder;
import de.bytemind.webservice.email.SendEmail;
import de.bytemind.webservice.server.BasicAuthenticationServer;
import de.bytemind.webservice.server.ByteMindServer;
import de.bytemind.webservice.server.Config;

public class EmailTest {

	public static void main(String[] args) {
		
		//Choose a server
		ByteMindServer server = new BasicAuthenticationServer();
		
		//Do some custom settings before loading the rest from file (note: file-settings overwrite custom)
		Config.redirectEmail = false;
		
		//Load server settings from file (and arguments)
		server.loadSettings(args);
		SendEmail emailClient = (SendEmail) ClassBuilder.construct(Config.email_module);
		
		//send
		String receiver = "test@example.com";
		sendRegistration(emailClient, receiver, "de");
		//sendRegistration(emailClient, receiver, "en");
		//sendChangePassword(emailClient, receiver, "de");
		//sendChangePassword(emailClient, receiver, "en");
	}
	
	public static void sendRegistration(SendEmail client, String receiver, String lang){
		String subject = "Please confirm your e-mail address and off we go";
		if (lang.equals(Language.DE.toValue())){
			subject = "Bitte bestätige deine E-Mail Adresse und los geht's";
		}
		String message = client.loadDefaultRegistrationMessage(lang, "id", "ticketid", "token", "time");
		//-send
		int code = client.send(receiver, message, subject, null);
		System.out.println("Registration mail sent with code: " + code);
		System.out.println("Error: " + ((client.getError() != null)? client.getError().getMessage() : "-"));
	}
	
	public static void sendChangePassword(SendEmail client, String receiver, String lang){
		String subject = "Here is the link to change your password";
		if (lang.equals(Language.DE.toValue())){
			subject = "Hier der Link zum Ändern deines Passworts";
		}
		String message = client.loadPasswordResetMessage(lang, "id", "ticketid", "token", "time");
		//-send
		int code = client.send(receiver, message, subject, null);
		System.out.println("Change password mail sent with code: " + code);
		System.out.println("Error: " + ((client.getError() != null)? client.getError().getMessage() : "-"));
	}

}
