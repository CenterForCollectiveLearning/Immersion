package edu.mit.media.immersion.reader;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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

import com.sun.mail.iap.ProtocolException;
import com.sun.mail.iap.Response;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPFolder.ProtocolCommand;
import com.sun.mail.imap.IMAPSSLStore;
import com.sun.mail.imap.protocol.IMAPProtocol;

import edu.mit.media.immersion.EmailData;

public class IMAPMetadataReader implements IMetadataReader {

	private String email;
	private IMAPSSLStore store = null;
	private IMAPFolder[] folders;
	private List<Message> allmsgs;
	private String displayName;

	public IMAPMetadataReader(String email, String username, String password,
			String host, int port) throws MessagingException,
			AllMailNotFoundException {
		if (email == null || password == null) {
			throw new IllegalArgumentException(
					"Email and password/access token fields are required");
		}
		this.email = email;
		Session session = Session.getInstance(System.getProperties(), null);
		this.store = (IMAPSSLStore) session.getStore("imaps");
		// accessing the mail server using the domain user and password
		store.connect(host, port, username, password);

		folders = getMailboxes(this.store);
		allmsgs = new ArrayList<Message>();

		for (final IMAPFolder folder : this.folders) {
			System.out.println(folder.getName());
			if (!folder.isOpen()) {
				folder.open(Folder.READ_ONLY);
			}
			Message[] msgs = folder.getMessagesByUID(1, UIDFolder.LASTUID);
			for (Message msg : msgs) {
				allmsgs.add(msg);
			}
		}
		Collections.sort(allmsgs, new Comparator<Message>() {
			@Override
			public int compare(Message o1, Message o2) {
				try {
					return (int) (((IMAPFolder) o1.getFolder()).getUID(o1) - ((IMAPFolder) o2
							.getFolder()).getUID(o2));
				} catch (MessagingException e) {
					return 0;
				}
			}
		});
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

	private IMAPFolder[] getMailboxes(IMAPSSLStore store)
			throws MessagingException {
		Folder defaultFolder = this.store.getDefaultFolder();
		Folder[] folders = defaultFolder.list("*");
		IMAPFolder[] result = new IMAPFolder[folders.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = (IMAPFolder) folders[i];
		}
		return result;
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
		List<Message> msgs = new ArrayList<Message>();
		for (int i = 0; i < allmsgs.size(); i++) {
			Message msg = allmsgs.get(i);
			if (((UIDFolder) msg.getFolder()).getUID(msg) > checkpointLong) {
				msgs.add(msg);
				if (msgs.size() >= nEmails) {
					break;
				}
			}
		}
		if (msgs.size() == 0) {
			return result;
		}
		final long startUID = ((IMAPFolder) msgs.get(0).getFolder())
				.getUID(msgs.get(0));
		final long endUID = ((IMAPFolder) msgs.get(msgs.size() - 1).getFolder())
				.getUID(msgs.get(msgs.size() - 1));
		final String emailAddr = this.email;
		for (final IMAPFolder folder : this.folders) {
			folder.doCommand(new ProtocolCommand() {
				@Override
				public Object doCommand(IMAPProtocol protocol)
						throws ProtocolException {
					Response[] responses = null;

					responses = protocol
							.command(
									"UID FETCH "
											+ startUID
											+ ":"
											+ endUID
											+ " (UID INTERNALDATE BODY.PEEK[HEADER.FIELDS (FROM TO CC)])",
									null);
					SimpleDateFormat dateParser = new SimpleDateFormat(
							"dd-MMM-yyyy HH:mm:ss Z");
					for (Response response : responses) {
						EmailData email = new EmailData();
						if (response.isOK()) {
							continue;
						}
						String[] lines = response.toString()
								.replaceAll("\r\n ", " ").split("\r\n");
						Map<String, String> headers = new HashMap<String, String>();
						for (int j = 1; j < lines.length; j++) {
							int idx = lines[j].indexOf(':');
							if (idx >= 0) {
								headers.put(lines[j].substring(0, idx)
										.toLowerCase(), lines[j]
										.substring(idx + 1));
							}
						}

						if (!headers.containsKey("from")) {
							continue;
						}
						try {
							InternetAddress[] from = InternetAddress
									.parse(headers.get("from"));
							if (from.length < 1) {
								continue;
							}
							email.from = from[0];
						} catch (AddressException e) {
							// System.out.println("from error" +
							// headers.get("from"));
							continue;
						}
						email.to = new ArrayList<InternetAddress>();
						try {
							if (headers.containsKey("to")) {
								email.to.addAll(Arrays.asList(InternetAddress
										.parse(headers.get("to"))));
							}
						} catch (AddressException ex) {
							// System.out.println("to error" +
							// headers.get("to"));
						}
						try {
							if (headers.containsKey("cc")) {

								email.to.addAll(Arrays.asList(InternetAddress
										.parse(headers.get("cc"))));

							}
						} catch (AddressException ex) {
							// System.out.println("cc error" +
							// headers.get("cc"));
						}
						try {
							email.timestamp = dateParser.parse(getItem(
									"INTERNALDATE", lines[0]));
						} catch (ParseException ex) {
							// System.out.println("date parse error :"
							// + getItem("INTERNALDATE", lines[0]));
							continue;
						}
						String uid = getItem("UID", lines[0]);
						email.thrid = uid;
						if (folder.getName().equals("Sent")
								|| email.from.getAddress().equals(emailAddr)) {
							email.isSent = true;
						} else {
							email.isSent = false;
						}
						if (email.isSent && email.from.getPersonal() != null) {
							String personal = email.from.getPersonal().trim()
									.toLowerCase();
							String address = email.from.getAddress().trim()
									.toLowerCase();
							if (!personal.equals("")
									&& !personal.equals(address)) {
								displayName = email.from.getPersonal();
							}
						}
						emails.add(email);
					}
					return null;
				}
			});
		}
		result.checkpoint = endUID + "";
		return result;
	}

	@Override
	public String getDisplayName() {
		if (this.displayName == null) {
			return "";
		} else {
			return this.displayName;
		}
	}
}
