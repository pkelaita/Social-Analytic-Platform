package driver;

import java.io.Console;
import java.util.ArrayList;

import javax.mail.AuthenticationFailedException;
import javax.mail.Message;
import javax.mail.MessagingException;

import com.jmodule.def.BoundedCommand;
import com.jmodule.def.Command;
import com.jmodule.def.CommandLogic;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;

import org.bson.Document;

import cache.DatabaseClient;
import cache.GmailClient;
import cache.MailContent;

/**
 * Sets up the commands for the email module
 * 
 * @author Pierce Kelaita
 */
public class GmailDriver {

	private static GmailClient client;
	private static String username;
	private static String password;
	private static boolean emailLogin = false;

	private final static String loginErrorMessage = "Error: please log in to your email first!\n";

	private static void fetch() throws MessagingException, AuthenticationFailedException {
		System.out.println("Updating inbox...");
		client = new GmailClient(username, password);
		client.retrieveMessages();
	}

	private static void login() {

		Console c = System.console();

		while (true) {

			try {
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

				fetch();

			} catch (AuthenticationFailedException e) {
				System.out.println("Wrong username or password!\n");
				continue;
			} catch (MessagingException e) {
				e.printStackTrace();
				continue;
			}
			break;
		}
	}

	public static ArrayList<Command> getCommands() {
		ArrayList<Command> commands = new ArrayList<>();

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
							fetch();
							DatabaseClient.addLast(client.getMessages(), Integer.parseInt(args[0]));
						} catch (NumberFormatException e) {
							System.err.println("invalid input!");
						} catch (AuthenticationFailedException e) {
							// This should never happen
						} catch (MessagingException e) {
							e.printStackTrace();
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
				MongoCollection<Document> col = DatabaseClient.getHeadersCollection();
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

				for (int i = top; i >= top - size; i--) {
					Document filter = new Document().append("index", i);
					MongoCursor<Document> cursor = col.find(filter).iterator();
					while (cursor.hasNext()) {
						Document mail = cursor.next();
						String from = ((String) mail.get("from"));
						String subject = (String) mail.get("subject");
						int index = (int) mail.get("index");
						printFormattedHeader(index, from, subject);
					}
				}
				System.out.println();
			}

		}, 0, 1);
		view.addReference("view");
		view.resetUsage("Usage: ~$ preview [number of emails]");
		view.appendUsage("       OR view ~");

		Command open = new Command("open", "Fetches a message at a given index and opens it",
				new CommandLogic(new String[] { "index" }) {

					@Override
					public void execute(String[] args) {
						if (!emailLogin) {
							System.err.println(loginErrorMessage);
							return;
						}
						int i = 0;
						try {
							i = Integer.parseInt(args[0]);
							MailContent m = new MailContent(client.getEmail(i), i, true);
							System.out.println(m.getHeader());
							System.out.println("Body:\n" + m.getBody());
						} catch (NumberFormatException nfe) {
							System.err.println("Invalid input!");
							return;
						} catch (IndexOutOfBoundsException ioob) {
							System.err.println("No message exists at index " + i);
						}
					}

				});

		Command grep = new Command("grep", "greps for sender", new CommandLogic(new String[] { "sequence" }) {

			@Override
			public void execute(String[] args) {
				if (!emailLogin) {
					System.err.println(loginErrorMessage);
					return;
				}
				for (int i = client.getSize() - 1; i >= 25000; i--) {
					try {
						Message m = client.getEmail(i);
						String from = m.getFrom()[0].toString();
						String subject = m.getSubject();
						if (from.toLowerCase().contains(args[0])) {
							printFormattedHeader(i, from, subject);
						}
					} catch (MessagingException e) {
						e.printStackTrace();
					}
				}
				System.out.println();
			}
		});

		commands.add(login);
		commands.add(last);
		commands.add(clear);
		commands.add(view);
		commands.add(open);
		commands.add(grep);

		return commands;
	}

	private static void printFormattedHeader(int index, String from, String subject) {
		final int SPACING = 40;

		from = from.trim();
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

		if (subject.length() > 85) {
			subject = subject.substring(0, 82) + "...";
		}

		System.out.print(index + "   ");
		System.out.println("From: " + from + "Subject: " + subject);
	}
}
