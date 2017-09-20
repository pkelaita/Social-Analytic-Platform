package cache;

import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.MessagingException;

/**
 * Utilizes the Javamail API to connect with POP3 mail server and access the
 * messages in the inbox
 * 
 * @author piercekelaita
 */
public class MailClient {

	private Message[] messages;
	private Store emailStore;

	/**
	 * Connects with the pop server and creates the POP3 store object
	 * 
	 * @param host
	 * @param storeType
	 * @param user
	 * @param password
	 * @throws MessagingException
	 */
	public MailClient(String host, String storeType, String user, String password) throws MessagingException {

		Properties properties = new Properties();
		properties.put("mail.pop3.host", host);
		properties.put("mail.pop3.port", "995");
		properties.put("mail.pop3.starttls.enable", "true");
		Session emailSession = Session.getDefaultInstance(properties);
		emailStore = emailSession.getStore("imaps");
		emailStore.connect(host, user, password);

	}

	/**
	 * Accesses the messages in the inbox of the email store and assigns them to an
	 * array
	 * 
	 * @throws MessagingException
	 */
	public void retrieveMessages() throws MessagingException {
		Folder emailFolder = emailStore.getFolder("INBOX");
		emailFolder.open(Folder.READ_ONLY);
		messages = emailFolder.getMessages();
	}

	/**
	 * Returns the email at a specified index in the inbox
	 * 
	 * @param index
	 * @return email at index
	 */
	public Message getEmail(int index) {
		Message email = messages[index];
		return email;
	}

	public Message[] getMessages() {
		return messages;
	}

	public int getSize() {
		return messages.length;
	}
}
