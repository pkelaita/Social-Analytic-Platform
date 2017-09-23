package driver;

import com.jmodule.def.Command;
import com.jmodule.exec.Module;
import com.jmodule.exec.ConsoleClient;

import java.util.logging.Level;
import java.util.logging.Logger;

import cache.DatabaseClient;

/**
 * Builds and runs the CLI using JModule
 * 
 * @author Pierce Kelaita
 */
public class App {
	public static void main(String[] args) {

		Logger mongoLogger = Logger.getLogger("org.mongodb");
		mongoLogger.setLevel(Level.SEVERE);

		Module gmail = new Module("gmail");
		for (Command c : GmailDriver.getCommands()) {
			gmail.addCommand(c);
		}

		ConsoleClient console = new ConsoleClient("Data Platform", new Module("home"));
		console.addModule(gmail);

		console.enableAlerts(true);
		console.enableTabCompletion(true);
		console.enableHistoryLogging(true);
		console.setPromptDisplayName("data-platform-v0.0.1");
		console.setModuleSeparator("/");
		console.setPromptSeparator(">");

		console.addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					DatabaseClient.closeMongoConnection();
				} catch (NullPointerException npe) {
				}
				System.out.println();
			}
		});

		console.runConsole();
	}
}
