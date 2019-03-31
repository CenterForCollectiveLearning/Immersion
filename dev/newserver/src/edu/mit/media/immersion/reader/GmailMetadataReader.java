package edu.mit.media.immersion.reader;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.UIDFolder;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.sun.mail.gimap.GmailFolder;
import com.sun.mail.gimap.GmailSSLStore;
import com.sun.mail.gimap.GmailStore;
import com.sun.mail.iap.ProtocolException;
import com.sun.mail.iap.Response;
import com.sun.mail.imap.IMAPFolder.ProtocolCommand;
import com.sun.mail.imap.protocol.IMAPProtocol;

import edu.mit.media.immersion.EmailData;
import edu.mit.media.immersion.reader.oauth2.OAuth2Authenticator;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;

public class GmailMetadataReader implements IMetadataReader {

	private String password;
	private String email;
	private GmailSSLStore store = null;
	private GmailFolder allMailFolder;
	private Message[] allmsgs;

	public static final int EMAILS_LIMIT = 500000;
	private static SimpleDateFormat dateParser = new SimpleDateFormat(
			"dd-MMM-yyyy HH:mm:ss Z");

	private static SimpleDateFormat oldDateParser1 = new SimpleDateFormat(
			"d MMM yyyy HH:mm:ss Z");

	private static SimpleDateFormat oldDateParser2 = new SimpleDateFormat(
			"d MMM yyyy HH:mm Z");

	public GmailMetadataReader(String email, String password,
			boolean withAccessToken) throws MessagingException,
			AllMailNotFoundException {
		if (email == null || password == null) {
			throw new IllegalArgumentException(
					"Email and password/access token fields are required");
		}
		this.email = email;
		this.password = password;
		if (withAccessToken) {
			OAuth2Authenticator.initialize();
			this.store = OAuth2Authenticator.connectToImap(this.email,
					this.password);
		} else {
			Session session = Session.getInstance(System.getProperties(), null);
			this.store = (GmailSSLStore) session.getStore("gimap");
			// accessing the mail server using the domain user and password
			store.connect("imap.gmail.com", 993, this.email, this.password);
		}
		String allMailMailbox = getAllMailMailbox(this.store);
		if (allMailMailbox == null) {
			throw new AllMailNotFoundException();
		}
		this.allMailFolder = (GmailFolder) this.store.getFolder(allMailMailbox);
		allMailFolder.open(Folder.READ_ONLY);
		allmsgs = allMailFolder.getMessagesByUID(1, UIDFolder.LASTUID);
		allmsgs = Arrays.copyOfRange(allmsgs,
				Math.max(0, allmsgs.length - EMAILS_LIMIT), allmsgs.length);
	}

	public static Date parseDate(String s) throws ParseException {
		s = s.trim().replaceFirst(" UT$", " UTC")
				.replaceFirst("^[a-zA-Z]{3},? ", "")
				.replaceFirst("\\(([a-zA-Z]+)\\)$", "$1");
		Date date = null;
		try {
			date = oldDateParser1.parse(s);
		} catch (ParseException ex) {
			date = oldDateParser2.parse(s);
		}
		return date;
	}

	private static String getItem(String itemName, String response) {
		int startIdx = response.indexOf(itemName) + itemName.length() + 1;
		String item = null;
		if (response.charAt(startIdx) == '"') {
			item = response.substring(startIdx + 1,
					response.indexOf('"', startIdx + 1));
		} else {
			item = response
					.substring(startIdx, response.indexOf(" ", startIdx));
		}
		return item;
	}

	public static String getAccessToken(String refreshToken) throws IOException {
		HttpTransport httpTransport = new NetHttpTransport();
		JsonFactory jsonFactory = new JacksonFactory();
		GoogleCredential credentials = new GoogleCredential.Builder()
				.setClientSecrets(
						"841281299045-70v0cs2bj9ee47jgba2sk70rcai13uo7.apps.googleusercontent.com",
						"xvLFh7QhWuINkMUaFqsZDwj4").setJsonFactory(jsonFactory)
				.setTransport(httpTransport).build()
				.setRefreshToken(refreshToken);
		// use the refresh token to get access token
		credentials.refreshToken();
		return credentials.getAccessToken();
	}

	private String getAllMailMailbox(GmailStore store)
			throws MessagingException {
		Folder defaultFolder = store.getDefaultFolder();
		for (Folder fld : defaultFolder.list("*")) {
			GmailFolder folder = (GmailFolder) fld;
			if (Arrays.asList(folder.getAttributes()).contains("\\All")) {
				return folder.getFullName();
			}
		}
		return null;
	}

	public static EmailData parseEmailFromResponse(String response) {
		EmailData email = new EmailData();
		String[] lines = response.replaceAll("\r\n ", " ").split("\r\n");
		Map<String, String> headers = new HashMap<String, String>();
		for (int j = 1; j < lines.length; j++) {
			int idx = lines[j].indexOf(':');
			if (idx >= 0) {
				headers.put(lines[j].substring(0, idx).toLowerCase(),
						lines[j].substring(idx + 1));
			}
		}

		if (!headers.containsKey("from")) {
			return null;
		}
		// email.subject = headers.get("subject");
		if (headers.containsKey("auto-submitted")) {
			email.auto = true;
		}
		try {
			InternetAddress[] from = InternetAddress.parse(headers.get("from"));
			if (from.length < 1) {
				return null;
			}
			email.from = from[0];
		} catch (AddressException e) {
			return null;
		}
		email.to = new ArrayList<InternetAddress>();
		try {
			if (headers.containsKey("to")) {
				email.to.addAll(Arrays.asList(InternetAddress.parse(headers
						.get("to"))));
			}
		} catch (AddressException ex) {
			;
		}
		try {
			if (headers.containsKey("cc")) {

				email.to.addAll(Arrays.asList(InternetAddress.parse(headers
						.get("cc"))));

			}
		} catch (AddressException ex) {
			;
		}
		try {
			if (!lines[0].contains("INTERNALDATE")) {
				// it is the old format, where there is Date
				if (!headers.containsKey("date")) {
					return null;
				}
				email.timestamp = parseDate(headers.get("date"));
			} else {
				email.timestamp = dateParser.parse(getItem("INTERNALDATE",
						lines[0]));

			}
		} catch (ParseException ex) {
			return null;
		}
		email.thrid = getItem("X-GM-THRID", lines[0]);
		if (lines[0].indexOf("\\Sent") >= 0) {
			email.isSent = true;
		} else {
			email.isSent = false;
		}
		email.UID = getItem("UID", lines[0]);
		return email;
	}

	@Override
	public MetadataResult readEmailsMetadata(String checkpoint, int nEmails)
			throws MessagingException {
		// prepare the result object
		final MetadataResult result = new MetadataResult();
		result.checkpoint = checkpoint;
		final List<EmailData> emails = new ArrayList<EmailData>();
		result.emails = emails;
		long checkpointLong = Long.parseLong(checkpoint);
		final List<Message> msgs = new ArrayList<Message>();
		for (int i = 0; i < allmsgs.length; i++) {
			Message msg = allmsgs[i];
			if (allMailFolder.getUID(msg) > checkpointLong) {
				msgs.add(msg);
				if (msgs.size() >= nEmails) {
					break;
				}
			}
		}
		if (msgs.size() == 0) {
			return result;
		}
		allMailFolder.doCommand(new ProtocolCommand() {
			@Override
			public Object doCommand(IMAPProtocol protocol)
					throws ProtocolException {
				Response[] responses = null;
				try {
					responses = protocol.command(
							"UID FETCH "
									+ allMailFolder.getUID(msgs.get(0))
									+ ":"
									+ allMailFolder
											.getUID(msgs.get(msgs.size() - 1))
									+ " (UID X-GM-LABELS X-GM-THRID INTERNALDATE BODY.PEEK[HEADER.FIELDS (FROM TO CC)])",
							null);
				} catch (MessagingException ex) {
					throw new ProtocolException(ex.getMessage());
				}
				for (Response response : responses) {
					if (response.isOK()) {
						continue;
					}
					try {
						EmailData email = parseEmailFromResponse(response
								.toString());
						emails.add(email);
						result.checkpoint = email.UID;
					} catch (Exception ex) {
						continue;
					}
				}
				return null;
			}
		});
		return result;
	}

	@Override
	public String getDisplayName() {
		throw new NotImplementedException();
	}
}
