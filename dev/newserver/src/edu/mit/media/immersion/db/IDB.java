package edu.mit.media.immersion.db;

import java.io.IOException;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mongodb.DBObject;

import edu.mit.media.immersion.EmailData;

public interface IDB {

	Iterable<State> getStates(DBObject query);

	State getState(String email, String studyid);

	void pushTaskObject(Task task);

	void pushTask(String email, String studyid, String authid);

	List<EmailData> getEmails(String email, String studyid, int version)
			throws IOException;

	Task popTask();

	void storeState(State state);

	void storeEmails(String email, String studyid, int version,
			List<EmailData> emails) throws IOException;

	AuthRequest getAuthRequest(String authid);

	void updateAuthRequest(String authid, AuthRequest request);

	JsonArray getEmailsJson(String email, String studyid, int version)
			throws IOException;

	void storeEmailsJson(String email, String studyid, int version,
			JsonArray array) throws IOException;

	JsonElement toJsonTree(Object obj);

	void log(String email, EmailType emailType, String module, String level,
			String msg);

	void logExc(String email, EmailType emailType, String module, Exception ex);
}