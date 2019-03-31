package edu.mit.media.immersion.reader.test;

import edu.mit.media.immersion.reader.GmailMetadataReader;

public class Test {
	public static void main(String[] args) throws Exception {
		String s = "Fri, 11 Jun 2010 19:15:55 GMT";
		System.out.println(GmailMetadataReader.parseDate(s));
		// IMetadataReader r = new IMAPMetadataReader("kknd_zekk@yahoo.com",
		// "kknd_zekk@yahoo.com", "broodwar", "imap.mail.yahoo.com", 993);
	}
}
