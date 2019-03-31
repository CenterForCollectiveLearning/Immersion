package edu.mit.media.immersion.reader;

import java.io.IOException;

import javax.mail.MessagingException;

public class HotmailPOP3MetadataReader extends POP3MetadataReader {

	public HotmailPOP3MetadataReader(String email, String password)
			throws MessagingException, IOException {
		super(email, password, email, "pop3.live.com", 995);
	}

}
