package driver;

import java.io.Console;

import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import com.jmodule.def.BoundedCommand;
import com.jmodule.def.Command;
import com.jmodule.def.CommandLogic;
import com.jmodule.exec.Module;
import com.jmodule.exec.ConsoleClient;

import cache.DatabaseClient;
import cache.MailClient;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates MailClient with given account information and adds the 10 most recent
 * messages to database
 * 
 * @author Pierce Kelaita
 */
public class Access {
	private static MailClient client;
	private static String username;
	private static boolean emailLogin = false;
	
	private final static String loginErrorMessage = "Error: please log in to your email first!\n";

	public static void login() {

		Console c = System.console();
		String password;

		while (true) {

			try {
				String host = "imap.gmail.com";
				String mailStoreType = "imaps";

				System.out.print("Enter email: ");
				username = c.readLine();
				if (username.length() == 0) {
					continue;
				}

				char[] pwd = c.readPassword("Enter password: ");
				password = "";
				for (char ch : pwd) {
					password += ch;
				}

				client = new MailClient(host, mailStoreType, username, password);
				client.retrieveMessages();

			} catch (AuthenticationFailedException e) {
				System.out.println("Wrong password!");
				continue;
			} catch (MessagingException e) {
				e.printStackTrace();
				continue;
			}
			break;
		}
	}

	public static void main(String[] args) {

		Logger mongoLogger = Logger.getLogger("org.mongodb");
		mongoLogger.setLevel(Level.OFF);
		
		Command login = new Command("account", "Log in or log out of your email account", new CommandLogic() {

			@Override
			public void execute(String[] args) {
				if (emailLogin) {
					boolean loop = true;
					while (loop) {
						loop = false;
						System.out.print("Log out of " + username + "? (y/n) ");
						switch (System.console().readLine()) {
						case "y":
							emailLogin = false;
							username = null;
							System.out.println();
							break;
						case "n":
							System.out.println();
							break;
						default:
							loop = true;
						}
						
					}
				} else {
					login();
					DatabaseClient.connectToDatabase();
					emailLogin = true;
				}
				
			}
			
		});
		login.addReference("acc");
				
		Command last = new Command("load", "Loads the last user-specified number of emails",
				new CommandLogic(new String[] { "number of emails" }) {

					@Override
					public void execute(String[] args) {
						if (!emailLogin) {
							System.err.println(loginErrorMessage);
							return;
						}
						try {
							DatabaseClient.addLast(client.getMessages(), Integer.parseInt(args[0]));
						} catch (NumberFormatException e) {
							System.err.println("invalid input!");
						}
						System.out.println();
					}
				});

		Command clear = new Command("clear", "clears the Mongo database", new CommandLogic() {

			@Override
			public void execute(String[] args) {
				if (!emailLogin) {
					System.err.println(loginErrorMessage);
					return;
				}
				long num = DatabaseClient.clearDB();
				System.out.println("Cleared " + num + " messages from database.\n");
			}
		});

		Command view = new BoundedCommand("preview", "previews the emails in the database", new CommandLogic() {

			@Override
			public void execute(String[] args) {
				if (!emailLogin) {
					System.err.println(loginErrorMessage);
					return;
				}
				MongoCollection<Document> col = DatabaseClient.getCol();
				int size = (int) col.count();
				if (args.length == 1) {
					try {
						size = Integer.parseInt(args[0]);
					} catch (NumberFormatException nfe) {
						System.err.println("Invalid input!");
						return;
					}
				}

				int top = client.getSize();
				final int SPACING = 40;

				for (int i = top; i >= top - size; i--) {
					Document filter = new Document().append("index", i);
					MongoCursor<Document> cursor = col.find(filter).iterator();
					while (cursor.hasNext()) {
						Document mail = cursor.next();
						String from = ((String) mail.get("from")).trim();
						if (from.contains("<")) {
							from = from.substring(0, from.indexOf('<') - 1);
						}
						if (from.length() > SPACING - 3) {
							from = from.substring(0, SPACING - 3);
						}
						String buffer = "";
						for (int s = 0; s < SPACING - from.length(); s++) {
							buffer += " ";
						}
						from += buffer;

						String subject = (String) mail.get("subject");
						if (subject.length() > 85) {
							subject = subject.substring(0, 82) + "...";
						}

						System.out.print(mail.get("index") + "   ");
						System.out.println("From: " + from + "Subject: " + subject);
					}
				}
				System.out.println();
			}

		}, 0, 1);
		view.addReference("view");
		view.resetUsage("Usage: ~$ preview [number of emails to preview]");
		view.appendUsage("    OR ~$ view ~");
		
		Module home = new Module("home");

		Module email = new Module("email");
		email.addCommand(login);
		email.addCommand(last);
		email.addCommand(clear);
		email.addCommand(view);
		email.appendHelpPage("\nUsername: " + username);

		ConsoleClient console = new ConsoleClient("Data Platform", home);
		console.addModule(email);

		console.enableAlerts(true);
		console.enableTabCompletion(true);
		console.enableHistoryLogging(true);
		console.setPromptDisplayName("data-platform-v0.0.1");
		console.setPromptSeparator(">");

		console.addShutdownHook(new Thread() {
			@Override
			public void run() {
				DatabaseClient.closeMongoConnection();
				System.out.println();
			}
		});

		console.runConsole();
	}
}
