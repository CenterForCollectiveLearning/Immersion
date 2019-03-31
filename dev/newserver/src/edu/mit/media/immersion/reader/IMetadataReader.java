package edu.mit.media.immersion.reader;

public interface IMetadataReader {
	int EMAILS_PER_FILE = 10 * 1000;
	int EMAILS_PER_FETCH = 1 * 1000;
	int MAX_NO_EMAIL = 300 * 1000;

	MetadataResult readEmailsMetadata(String checkpoint, int nEmails)
			throws Exception;

	String getDisplayName();
}
