package cache;

import javax.mail.Message;

import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/**
 * Connects to Mongo database and adds specified emails. Must have a 'mongod'
 * instance running
 * 
 * @author Pierce Kelaita
 */
public class DatabaseClient {

	private static final String DATABASE_NAME = "KELAITA_DB";
	private static final String COLLECTION_NAME_1 = "HEADERS";
	private static final String COLLECTION_NAME_2 = "EMAILS";
	private static final int DEFAULT_PORT = 27017;

	private static MongoDatabase database;
	private static MongoCollection<Document> headers;
	private static MongoCollection<Document> emails;
	private static MongoClient client;

	/**
	 * Pings Mongo to ensure a secure connection and retrieves information from the
	 * database with the specified default information
	 */
	public static void connectToDatabase() throws MongoTimeoutException {

		System.out.println("Connecting to database...");

		// check connection
		MongoClient pc = new MongoClient("localhost", DEFAULT_PORT);
		System.out.println(pc.listDatabaseNames().first());
		pc.close();

		// connect to database and grab collections
		client = new MongoClient("localhost", DEFAULT_PORT);
		System.out.println("Server connection successful @ localhost:" + DEFAULT_PORT);
		database = client.getDatabase(DATABASE_NAME);
		headers = database.getCollection(COLLECTION_NAME_1);
		System.out.println("Database connection successful @ " + DATABASE_NAME + "." + COLLECTION_NAME_1);
		emails = database.getCollection(COLLECTION_NAME_2);
		System.out.println("Database connection successful @ " + DATABASE_NAME + "." + COLLECTION_NAME_2 + "\n");

	}

	/**
	 * Returns a collection of emails with only headers
	 * 
	 * @return headers
	 */
	public static MongoCollection<Document> getHeadersCollection() {
		return headers;
	}

	/**
	 * Returns a colleciton of emails with headers and bodies
	 * 
	 * @return emails
	 */
	public static MongoCollection<Document> getEmailsCollection() {
		return emails;
	}

	/**
	 * Adds a given number of parsed messages to the database, starting with the
	 * most recent message
	 * 
	 * @param messages
	 *            inbox messages generated by MailClient
	 * @param last
	 *            the number of recent messages to add to databse
	 */
	public static void addLast(Message[] messages, int last) {
		System.out.println("Adding emails to database...");
		int done = 0;
		for (int i = messages.length - 1; i > messages.length - last - 1; i--) {
			headers.insertOne(new MailContent(messages[i], i, false).toBson());

			done++;
			printProgressBar(done, last);
		}
		System.out.println("\nAdded last " + last + " emails to database.");

	}

	public static void printProgressBar(int done, int total) {
		final int bar_length = 50;
		double perc = (double) done / (double) total;

		System.out.print("\r[");
		int i = 0;
		for (; i <= (int) (perc * bar_length); i++) {
			System.out.print("=");
		}
		for (; i < bar_length; i++) {
			System.out.print(" ");
		}

		long percOut = Math.round(perc * 100);
		System.out.print("] " + percOut + "% [" + done + "/" + total + "]");
	}

	public static long clearDB() {
		long removed = headers.count();
		headers.deleteMany(new Document());
		return removed;
	}

	public static MongoDatabase getDB() {
		return database;
	}

	public static void closeMongoConnection() {
		client.close();
	}
}
