package edu.mit.media.immersion;

import java.util.Date;
import java.util.Scanner;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import edu.mit.media.immersion.db.AuthRequest;
import edu.mit.media.immersion.db.AuthStatus;
import edu.mit.media.immersion.db.DBMongo;
import edu.mit.media.immersion.db.EmailType;
import edu.mit.media.immersion.db.LogLevel;
import edu.mit.media.immersion.db.State;
import edu.mit.media.immersion.db.State.UserInfo;
import edu.mit.media.immersion.db.Task;
import edu.mit.media.immersion.reader.AllMailNotFoundException;
import edu.mit.media.immersion.reader.ExchangeMetadataReader;
import edu.mit.media.immersion.reader.GmailMetadataReader;
import edu.mit.media.immersion.reader.HotmailPOP3MetadataReader;
import edu.mit.media.immersion.reader.IMAPMetadataReader;
import edu.mit.media.immersion.reader.IMetadataReader;
import edu.mit.media.immersion.reader.MetadataResult;
import edu.mit.media.immersion.reader.POP3MetadataReader;

public class Fetcher implements Runnable {
	public static boolean die = false;

	public static final int MIN_EMAILS_PER_FILE = 10000;

	public void run() {
		DBMongo db = new DBMongo();
		JsonParser parser = new JsonParser();
		while (!die) {
			Task task = db.popTask();
			if (task == null) {
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					System.out.println("Interrupted");
					return;
				}
				continue;
			}
			State state = db.getState(task.email, task.studyid);
			EmailType emailType = EmailType.GMAIL;
			String email = task.email;
			AuthRequest authRequest = null;
			if (task.authid != null) {
				authRequest = db.getAuthRequest(task.authid);
				emailType = authRequest.emailType;
			}
			IMetadataReader reader = null;
			try {
				Date timestamp = task.timestamp;
				// if the task is to be served in the future
				if (timestamp.getTime() > new Date().getTime()) {
					db.pushTaskObject(task);
					continue;
				}
				switch (emailType) {
				case GMAIL:
					db.log(email, EmailType.GMAIL, "Fetcher", LogLevel.INFO,
							"Processing");
					JsonObject credentials = parser.parse(state.credentials)
							.getAsJsonObject();
					String refreshToken = null;
					if (credentials.has("refresh_token")
							&& !credentials.get("refresh_token").isJsonNull()) {
						refreshToken = credentials.get("refresh_token")
								.getAsString();
					}
					String accessToken = null;
					if (refreshToken != null && refreshToken != "") {
						accessToken = GmailMetadataReader
								.getAccessToken(refreshToken);
					} else {
						accessToken = credentials.get("access_token")
								.getAsString();
					}
					reader = new GmailMetadataReader(email, accessToken, true);
					break;
				case EXCHANGE:
					db.log(email, EmailType.EXCHANGE, "Fetcher", LogLevel.INFO,
							"Processing");
					// String username = email.substring(0, email.indexOf('@'));
					reader = new ExchangeMetadataReader(authRequest.username,
							authRequest.password, email);
					break;
				case HOTMAIL:
					db.log(email, EmailType.HOTMAIL, "Fetcher", LogLevel.INFO,
							"Processing");
					reader = new HotmailPOP3MetadataReader(email,
							authRequest.password);
					break;
				case YAHOO:
					db.log(email, EmailType.YAHOO, "Fetcher", LogLevel.INFO,
							"Processing");
					reader = new IMAPMetadataReader(email, email,
							authRequest.password, "imap.mail.yahoo.com", 993);
					break;
				}
			} catch (AllMailNotFoundException ex) {
				db.log(email, emailType, "Fetcher", LogLevel.WARNING,
						"'All mail' not found");
				state.imap = true;
				db.storeState(state);
				continue;
				// add the fetching task again to the queue with 3min delay
				// task.timestamp = new Date(new Date().getTime() + 3 * 60 *
				// 1000);
				// db.pushTaskObject(task);
			} catch (Exception ex) {
				// update auth request
				if (authRequest != null) {
					authRequest.status = AuthStatus.FAILED;
					db.updateAuthRequest(task.authid, authRequest);
				}
				db.logExc(email, emailType, "Fetcher", ex);
				continue;
			}

			db.log(email, emailType, "Fetcher", LogLevel.INFO,
					"Successfully authorized");
			try {
				// create a new state if state is null
				if (state == null) {
					// if state is null, it's up to the reader to find
					// user's basic info
					String displayName = reader.getDisplayName();
					String[] names = displayName.split(" ");
					String firstName = names[0];
					String lastName = "";
					if (names.length > 1) {
						lastName = names[names.length - 1];
					}
					UserInfo info = new UserInfo(displayName, firstName,
							lastName, email);
					state = new State(email, task.studyid, -1, "0", null,
							false, false, info);
					db.storeState(state);
				}

				// user is successfully authorized, so update the auth status
				if (authRequest != null) {
					authRequest.status = AuthStatus.AUTHORIZED;
					db.updateAuthRequest(task.authid, authRequest);
				}

				// update state
				if (state.imap) {
					state.imap = false;
				}
				state.working = true;
				db.storeState(state);

				JsonArray prevEmails = null;
				int version = state.version;
				if (version >= 0) {
					prevEmails = db.getEmailsJson(email, task.studyid, version);
					// recover the true version number
					while (prevEmails == null && version >= 0) {
						version--;
						prevEmails = db.getEmailsJson(email, task.studyid,
								version);
					}
				} else {
					// start a new file
					version++;
					prevEmails = new JsonArray();
				}
				MetadataResult result = new MetadataResult();
				result.checkpoint = state.lastuid;
				while (true) {
					int nemails = 10000;
					if (reader instanceof POP3MetadataReader) {
						nemails = 200;
					} else if (reader instanceof ExchangeMetadataReader) {
						nemails = 5000;
					}
					result = reader.readEmailsMetadata(result.checkpoint,
							nemails);
					if (result.emails.size() == 0) {
						break;
					}
					// Check if the reader found updated user-info
					// after reading the new batch of e-mails
					try {
						String displayName = reader.getDisplayName();
						String[] names = displayName.split(" ");
						String firstName = names[0];
						String lastName = "";
						if (names.length > 1) {
							lastName = names[names.length - 1];
						}
						UserInfo info = new UserInfo(displayName, firstName,
								lastName, email);
						state.userinfo = info;
					} catch (NotImplementedException ex) {
						;
					}
					prevEmails.addAll(db.toJsonTree(result.emails)
							.getAsJsonArray());
					db.storeEmailsJson(email, task.studyid, version, prevEmails);
					// update state
					state.version = version;
					state.lastuid = result.checkpoint;
					db.storeState(state);
					db.log(email, emailType, "Fetcher", LogLevel.INFO,
							"Version " + version + " stored in db");
					if (prevEmails.size() > MIN_EMAILS_PER_FILE) {
						// start a new file
						version++;
						prevEmails = new JsonArray();
					}
				}
				if (state.working) {
					state.working = false;
				}
				// delete the refresh tokens for security reasons
				if (state.credentials != null) {
					state.credentials = null;
				}
				db.storeState(state);
				db.log(email, emailType, "Fetcher", LogLevel.INFO,
						"Done processing");
			} catch (NullPointerException ex) {
				db.logExc(email, emailType, "Fetcher", ex);
			} catch (Exception ex) {
				db.logExc(email, emailType, "Fetcher", ex);
				// retry again
				db.pushTask(task.email, task.studyid, task.authid);
			}
		}
		System.out.println("Thread exited gracefully");
	}

	public static void main(String[] args) throws InterruptedException {
		int nthreads = -1;
		if (args.length < 1) {
			nthreads = 30;
		} else {
			nthreads = Integer.parseInt(args[0]);
		}
		Thread[] threads = new Thread[nthreads];
		for (int i = 0; i < nthreads; i++) {
			threads[i] = new Thread(new Fetcher());
			threads[i].start();
		}
		System.out.println("Started " + nthreads + " threads");
		Scanner scanner = new Scanner(System.in);
		while (true) {
			String line = scanner.nextLine();
			if (line.equals("exit")) {
				System.out.println("Sending exit signal");
				Fetcher.die = true;
				// wait for all the threads to finish
				// for (int i = 0; i < nthreads; i++) {
				// threads[i].interrupt();
				// }
				for (int i = 0; i < nthreads; i++) {
					threads[i].join();
				}
				System.out.println("Main thread exited gracefully");
				break;
			}
		}
		scanner.close();
	}
}
