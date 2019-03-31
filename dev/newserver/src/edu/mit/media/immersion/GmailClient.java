package edu.mit.media.immersion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.search.FlagTerm;
import javax.mail.search.FromTerm;

import com.sun.mail.gimap.GmailFolder;
import com.sun.mail.gimap.GmailFolder.FetchProfileItem;
import com.sun.mail.gimap.GmailMessage;
import com.sun.mail.gimap.GmailSSLStore;
import com.sun.mail.gimap.GmailStore;

import edu.mit.media.immersion.reader.oauth2.OAuth2Authenticator;

public class GmailClient implements EmailClient {

	private String email;
	private String password;
	private GmailSSLStore store;
	public Folder allMailbox;
	public Folder inbox;

	public void login(String email, String password, boolean withAccessToken)
			throws MessagingException {
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
			this.store = (GmailSSLStore) session.getStore("gimaps");
			// accessing the mail server using the domain user and password
			store.connect("imap.gmail.com", 993, this.email, this.password);
		}

		// open inbox and allmail
		this.allMailbox = getAllMailMailbox(store);
		// this.allMailbox.open(Folder.READ_ONLY);
		this.inbox = store.getFolder("INBOX");
		// this.inbox.open(Folder.READ_ONLY);
	}

	private static Folder getAllMailMailbox(GmailStore store)
			throws MessagingException {
		Folder defaultFolder = store.getDefaultFolder();
		for (Folder fld : defaultFolder.list("*")) {
			GmailFolder folder = (GmailFolder) fld;
			if (Arrays.asList(folder.getAttributes()).contains("\\All")) {
				return folder;
			}
		}
		return null;
	}

	public List<Message> getUnreadMessages() throws MessagingException,
			IOException {
		if (!inbox.isOpen()) {
			inbox.open(Folder.READ_ONLY);
		}
		Message[] msgs = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN),
				false));
		System.out.println("New unread messages: " + msgs.length);
		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		fp.add(FetchProfile.Item.CONTENT_INFO);
		inbox.fetch(msgs, fp);
		for (int i = 0; i < msgs.length; i++) {
			System.out.println(getText(msgs[i]));
			System.out.println("------------------------");
		}
		return null;

	}

	private static String getText(Part p) throws MessagingException,
			IOException {
		if (p.isMimeType("text/*")) {
			String s = (String) p.getContent();
			// boolean textIsHtml = p.isMimeType("text/html");
			return s;
		}

		if (p.isMimeType("multipart/alternative")) {
			// prefer plain text over html text
			Multipart mp = (Multipart) p.getContent();
			String text = null;
			for (int i = 0; i < mp.getCount(); i++) {
				Part bp = mp.getBodyPart(i);
				if (bp.isMimeType("text/html")) {
					if (text == null)
						text = getText(bp);
					continue;
				} else if (bp.isMimeType("text/plain")) {
					String s = getText(bp);
					if (s != null) {
						return s;
					}
				} else {
					return getText(bp);
				}
			}
			return text;
		} else if (p.isMimeType("multipart/*")) {
			Multipart mp = (Multipart) p.getContent();
			for (int i = 0; i < mp.getCount(); i++) {
				String s = getText(mp.getBodyPart(i));
				if (s != null)
					return s;
			}
		}
		return null;
	}

	public List<EmailData> searchFrom(String fromEmail)
			throws AddressException, MessagingException, IOException {
		if (!this.allMailbox.isOpen()) {
			this.allMailbox.open(Folder.READ_ONLY);
		}
		List<EmailData> results = new ArrayList<EmailData>();
		Message[] msgs = this.allMailbox.search(new FromTerm(
				new InternetAddress(fromEmail)));
		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		fp.add(FetchProfileItem.THRID);
		fp.add(FetchProfile.Item.CONTENT_INFO);
		allMailbox.fetch(msgs, fp);
		long start = System.currentTimeMillis();
		for (Message msg : msgs) {
			GmailMessage gmailMsg = (GmailMessage) msg;
			EmailData emailData = new EmailData();
			// emailData.body = getText(msg);
			emailData.subject = msg.getSubject();
			Address[] fromTmp = msg.getFrom();
			if (fromTmp == null || fromTmp.length < 1) {
				// skip this invalid email
				continue;
			}
			emailData.from = (InternetAddress) msg.getFrom()[0];

			List<InternetAddress> to = new ArrayList<>();
			Address[] toTmp = msg.getRecipients(RecipientType.TO);
			if (toTmp != null && toTmp.length > 0) {
				for (Address toAddr : toTmp) {
					to.add((InternetAddress) toAddr);
				}
			}
			Address[] ccTmp = msg.getRecipients(RecipientType.CC);
			if (ccTmp != null && ccTmp.length > 0) {
				for (Address ccAddr : ccTmp) {
					to.add((InternetAddress) ccAddr);
				}
			}
			emailData.to = to;
			emailData.timestamp = msg.getReceivedDate();
			emailData.thrid = gmailMsg.getThrId() + "";
			// boolean isSent = false;
			// TODO: check if the email is sent
			results.add(emailData);
		}
		System.out.println(System.currentTimeMillis() - start);
		return results;
	}

	public static void main(String[] args) throws MessagingException,
			IOException {
		GmailClient client = new GmailClient();
		client.login("dsmilkov@gmail.com", "Lif3isg00d", false);

		for (EmailData emailData : client.searchFrom("angelstanoev@gmail.com")) {
			System.out.println(emailData.subject);
		}
	}

}
