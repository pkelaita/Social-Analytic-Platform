package driver;

import java.io.Console;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.*;

import com.jModule.def.Command;
import com.jModule.def.CommandLogic;
import com.jModule.exec.Module;
import com.jModule.exec.ConsoleClient;

import cache.DatabaseClient;
import cache.MailClient;

import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;

/**
 * Creates MailClient with given account information and adds the 10 most recent
 * messages to database
 * 
 * @author Pierce Kelaita
 */
public class Access {
	private static MailClient client;
	private static String username;

	public static void login() {

		// disable logging, works in parallel with log4j.properties
		@SuppressWarnings("unchecked")
		List<Logger> loggers = Collections.<Logger>list(LogManager.getCurrentLoggers());
		loggers.add(LogManager.getRootLogger());
		for (Logger logger : loggers) {
			logger.setLevel(Level.OFF);
		}

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
		DatabaseClient.connectToDatabase();
	}

	public static void main(String[] args) {

		login();

		Command last = new Command("load", "Loads the last user-specified number of emails",
				new CommandLogic(new String[] { "number of emails" }) {

					@Override
					public void execute(String[] args) {
						try {
							DatabaseClient.addLast(client.getMessages(), Integer.parseInt(args[0]));
						} catch (NumberFormatException e) {
							System.err.println("invalid input!");
						}
					}
				});

		Command clear = new Command("clear", "clears the Mongo database", new CommandLogic() {

			@Override
			public void execute(String[] args) {
				DatabaseClient.clearDB();
			}
		});

		Module main = new Module("");
		main.addCommand(last);
		main.addCommand(clear);
		main.appendHelpPage("\nUsername: " + username);

		ConsoleClient console = new ConsoleClient("Data Platform", main);

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
