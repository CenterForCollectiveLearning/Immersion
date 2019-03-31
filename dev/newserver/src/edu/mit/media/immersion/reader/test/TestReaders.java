package edu.mit.media.immersion.reader.test;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogManager;

import javax.mail.MessagingException;

import edu.mit.media.immersion.reader.AllMailNotFoundException;
import edu.mit.media.immersion.reader.ExchangeMetadataReader;
import edu.mit.media.immersion.reader.GmailMetadataReader;
import edu.mit.media.immersion.reader.HotmailPOP3MetadataReader;
import edu.mit.media.immersion.reader.IMAPMetadataReader;
import edu.mit.media.immersion.reader.IMetadataReader;
import edu.mit.media.immersion.reader.MetadataResult;

public class TestReaders {

	public static IMetadataReader prepareExchangeReader() throws Exception {
		LogManager.getLogManager().getLogger("").setLevel(Level.WARNING);
		ExchangeMetadataReader reader = new ExchangeMetadataReader("smilkov",
				"Love=Life", "smilkov@mit.edu");
		return reader;
	}

	public static IMetadataReader prepareGIMAPReader()
			throws MessagingException, IOException, AllMailNotFoundException {
		String refreshToken = "1/440pOpAY3iONrFfVBbg5izgMtE-UQnt4RLHo-P0sjTM";
		String accessToken = GmailMetadataReader.getAccessToken(refreshToken);
		IMetadataReader reader = new GmailMetadataReader("dsmilkov@gmail.com",
				accessToken, true);
		return reader;
	}

	public static IMetadataReader prepareYahooReader()
			throws MessagingException, AllMailNotFoundException {
		return new IMAPMetadataReader("kknd_zekk@yahoo.com",
				"kknd_zekk@yahoo.com", "broodwar", "imap.mail.yahoo.com", 993);
	}
	
	public static IMetadataReader prepareHotmailReader()
			throws MessagingException, AllMailNotFoundException {
		return new IMAPMetadataReader("smilkov@live.com",
				"smilkov@live.com", "Love=Life", "imap-mail.outlook.com", 993);
	}

	public static IMetadataReader prepareLivePOP3Reader()
			throws MessagingException, IOException {
		return new HotmailPOP3MetadataReader("smilkov@live.com", "Love=Life");
	}

	public static void main(String[] args) throws Exception {
		// IMetadataReader reader = prepareExchangeReader();
		// IMetadataReader reader = prepareGIMAPReader();
		// IMetadataReader reader = prepareLivePOP3Reader();
		//IMetadataReader reader = prepareYahooReader();
		IMetadataReader reader = prepareHotmailReader();
		MetadataResult result = new MetadataResult();
		result.checkpoint = "0";
		while (true) {
			long start = System.currentTimeMillis();
			result = reader.readEmailsMetadata(result.checkpoint, 10000);
			System.out.println("checkpoint: " + result.checkpoint);
			System.out.println("# emails: " + result.emails.size());
			if (result.emails.size() == 0) {
				break;
			}
			long end = System.currentTimeMillis();
			System.out.println("Time elapsed: " + (end - start) / 1000
					+ " seconds");
		}
	}
}
