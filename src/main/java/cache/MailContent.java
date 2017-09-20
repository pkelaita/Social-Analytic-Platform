package cache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;

import org.jsoup.Jsoup;

import org.bson.Document;

/**
 * Parses and stores Javamail properties
 * 
 * @author piercekelaita
 */
public class MailContent {

	private int index;
	private Date date;
	private String subject;
	private String from;
	private String body;

	/**
	 * Constructor. Grabs properties from Javamail messages and stores their info in
	 * Java-native formats
	 * 
	 * @param email
	 *            Javamail message
	 * @param index
	 *            known index of message in folder, useful for parsing error
	 *            messages
	 */
	public MailContent(Message email, int index) {
		this.index = index;

		try {
			date = email.getReceivedDate();

			subject = email.getSubject();
			from = "";
			body = "";
			for (Address a : email.getFrom()) {
				from += a + "\t";
			}
			try {
				body = parseBody(email, index);
			} catch (IOException e) {
				e.printStackTrace();
			}

		} catch (MessagingException e1) {
			e1.printStackTrace();
		}
	}

	public int getIndex() {
		return index;
	}

	public Date getDate() {
		return date;
	}

	public String getSubject() {
		return subject;
	}

	public String getFrom() {
		return from;
	}

	public String getBody() {
		return body;
	}

	/**
	 * Parses email content either as a message of a single MIME type or a group of
	 * parts of multiple MIME types
	 * 
	 * @param message
	 * @param index
	 * @return
	 * @throws MessagingException
	 * @throws IOException
	 */
	public String parseBody(Message message, int index) throws MessagingException, IOException {
		ArrayList<String> content = new ArrayList<String>();

		try {
			// parse single-part message based on type
			if (message.isMimeType("text/plain")) {
				content.add(message.getContent().toString());
			} else if (message.isMimeType("text/html")) {
				String html = (String) message.getContent();
				content.add(Jsoup.parse(html).text());

			} else if (message.isMimeType("multipart/*")) {
				MimeMultipart mmp = (MimeMultipart) message.getContent();

				// iterate through each part of message content
				for (int i = 0; i < mmp.getCount(); i++) {
					BodyPart part = mmp.getBodyPart(i);

					// parse each part based on type
					if (part.isMimeType("text/plain")) {
						content.add(part.getContent().toString());
						break;
					} else if (part.isMimeType("text/html")) {
						String html = (String) part.getContent();
						content.add(Jsoup.parse(html).text());
					}
				}
			}

		} catch (Exception e) {
			return "Error parsing email number " + index + ":\n" + e.getStackTrace();
		}
		String result = "";
		for (String line : content) {
			if (line.trim().length() > 0) {
				result += line + "\n";
			}
		}
		return result;
	}

	/**
	 * Converts mail content to a BSON document that can be loaded into a Mongo
	 * database
	 * 
	 * @return parsed mail content
	 */
	public Document toBson() {
		Document doc = new Document().append("index", index);
		doc.append("date", date.toString());
		doc.append("subject", subject);
		doc.append("from", from);
		doc.append("body", body);
		return doc;
	}

	/**
	 * Displays mail content in a user-friendly format
	 */
	@Override
	public String toString() {
		String out = "Email index: " + index;
		out += "\nDate: " + date;
		out += "\nSubject: " + subject;
		out += "\nFrom: " + from;
		out += "\nBody:\n" + body;
		return out;
	}

}
