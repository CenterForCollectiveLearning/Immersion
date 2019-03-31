package edu.mit.media.immersion.reader;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.UIDFolder;
import javax.mail.internet.InternetAddress;

import com.sun.mail.pop3.POP3Folder;
import com.sun.mail.pop3.POP3SSLStore;

import edu.mit.media.immersion.EmailData;

public abstract class POP3MetadataReader implements IMetadataReader {
	private String email;
	// initialize the display name with empty string and later update it as you
	// process e-mails
	private String displayName = null;
	private Message[] msgs;
	private POP3Folder folder;

	public POP3MetadataReader(String username, String password, String email,
			String host, int port) throws MessagingException, IOException {
		this.email = email;

		Properties pop3Props = new Properties();
		pop3Props.setProperty("mail.pop3s.port", "995");

		pop3Props.setProperty("mail.pop3s.pipelining", "true");
		pop3Props.setProperty("mail.pop3.pipelining", "true");

		pop3Props.setProperty("mail.pop3s.disablecapa", "true");
		pop3Props.setProperty("mail.pop3.disablecapa", "true");

		Session session = Session.getInstance(pop3Props, null);
		session.setDebug(true);
		session.setDebugOut(new PrintStream("debug.txt"));
		POP3SSLStore store = (POP3SSLStore) session.getStore("pop3s");
		store.connect(host, port, username, password);
		folder = (POP3Folder) store.getFolder("INBOX");
		folder.open(POP3Folder.READ_ONLY);

		msgs = folder.getMessages();
		FetchProfile fp = new FetchProfile();
		fp.add(UIDFolder.FetchProfileItem.UID);
		folder.fetch(msgs, fp);
	}

	@Override
	public MetadataResult readEmailsMetadata(String checkpoint, int nEmails)
			throws MessagingException {
		// prepare the result object
		final MetadataResult result = new MetadataResult();
		result.checkpoint = checkpoint;
		final List<EmailData> emails = new ArrayList<EmailData>();
		result.emails = emails;

		int idx = -1;
		for (int i = 0; i < msgs.length; i++) {
			Message msg = msgs[i];
			if (folder.getUID(msg).equals(checkpoint)) {
				idx = i;
				break;
			}
		}
		Message[] innerMsgs = Arrays.copyOfRange(msgs, idx + 1,
				Math.min(idx + 1 + nEmails, msgs.length));
		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		folder.fetch(innerMsgs, fp);
		for (int i = 0; i < innerMsgs.length; i++) {
			Message popMsg = innerMsgs[i];
			EmailData email = new EmailData();
			try {
				Address[] tmpFrom = popMsg.getFrom();
				// an email must have a sender
				if (tmpFrom == null || tmpFrom.length < 1) {
					continue;
				}
				email.from = (InternetAddress) tmpFrom[0];
			} catch (MessagingException ex) {
				// an email must have a sender
				continue;
			}
			email.to = new ArrayList<InternetAddress>();
			// TO
			try {

				if (popMsg.getRecipients(RecipientType.TO) != null) {
					for (Address addr : popMsg.getRecipients(RecipientType.TO)) {
						email.to.add((InternetAddress) addr);
					}
				}
			} catch (MessagingException ex) {
				;
			}
			// CC
			try {
				if (popMsg.getRecipients(RecipientType.CC) != null) {
					for (Address addr : popMsg.getRecipients(RecipientType.CC)) {
						email.to.add((InternetAddress) addr);
					}
				}
			} catch (MessagingException ex) {
				;
			}
			// each email in its own little thread
			try {
				email.thrid = folder.getUID(popMsg);
			} catch (MessagingException ex) {
				// an email must have UID
				continue;
			}
			try {
				// an email must have a date
				if (popMsg.getSentDate() == null) {
					continue;
				}
				email.timestamp = popMsg.getSentDate();
			} catch (MessagingException ex) {
				// an email must have a date
				continue;
			}

			email.isSent = email.from.getAddress().toLowerCase()
					.equals(this.email.toLowerCase());
			if (this.displayName == null && email.isSent) {
				String personal = email.from.getPersonal().trim().toLowerCase();
				String address = email.from.getAddress().trim().toLowerCase();
				if (!personal.equals("") && !personal.equals(address)) {
					this.displayName = email.from.getPersonal();
				}
			}
			emails.add(email);
			// set checkpoint to UID
			result.checkpoint = email.thrid;
		}
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
