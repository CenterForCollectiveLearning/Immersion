package edu.mit.media.immersion.db;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.mail.internet.InternetAddress;

import org.bson.types.ObjectId;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import edu.mit.media.immersion.EmailData;
import edu.mit.media.immersion.db.State.UserInfo;
import edu.mit.media.immersion.reader.GmailMetadataReader;

class DateSerializer implements JsonSerializer<Date> {

	public JsonElement serialize(Date src, Type typeOfSrc,
			JsonSerializationContext context) {
		return new JsonPrimitive(src.getTime() / 1000);
	}
}

class DateDeserializer implements JsonDeserializer<Date> {

	public Date deserialize(JsonElement json, Type typeOfT,
			JsonDeserializationContext context) throws JsonParseException {
		return new Date(json.getAsLong() * 1000);
	}

}

class InternetAddressDeserializer implements JsonDeserializer<InternetAddress> {

	public InternetAddress deserialize(JsonElement json, Type typeOfT,
			JsonDeserializationContext context) throws JsonParseException {
		JsonArray tmp = json.getAsJsonArray();
		try {
			return new InternetAddress(tmp.get(1).getAsString(), tmp.get(0)
					.getAsString());
		} catch (UnsupportedEncodingException e) {
			throw new JsonParseException(e.getMessage());
		}
	}

}

class InternetAddressSerializer implements JsonSerializer<InternetAddress> {

	public JsonElement serialize(InternetAddress src, Type typeOfSrc,
			JsonSerializationContext context) {
		JsonArray result = new JsonArray();
		if (src.getPersonal() == null) {
			result.add(new JsonPrimitive(src.getAddress()));
		} else {
			result.add(new JsonPrimitive(src.getPersonal()));
		}
		result.add(new JsonPrimitive(src.getAddress()));
		return result;
	}
}

public class DBMongo {
	public static DB db = null;
	private static Gson gson = null;
	static {
		gson = new GsonBuilder()
				.registerTypeAdapter(Date.class, new DateSerializer())
				.registerTypeAdapter(Date.class, new DateDeserializer())
				.registerTypeAdapter(InternetAddress.class,
						new InternetAddressDeserializer())
				.registerTypeAdapter(InternetAddress.class,
						new InternetAddressSerializer()).create();
		try {
			db = new MongoClient().getDB("immersion");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	public AuthRequest getAuthRequest(String authid) {
		DBObject obj = db.getCollection("authrequests").findOne(
				new BasicDBObject("_id", new ObjectId(authid)));
		if (obj == null) {
			return null;
		}
		String studyid = null;
		if (obj.containsField("studyid")) {
			studyid = obj.get("studyid").toString();
		}
		return new AuthRequest(obj.get("email").toString(), obj.get("username")
				.toString(), obj.get("password").toString(),
				EmailType.fromValue((int) obj.get("emailType")),
				AuthStatus.fromValue((int) obj.get("status")), studyid);
	}

	public void pushTask(String email, String studyid, String authid) {
		DBObject task = new BasicDBObject();
		task.put("email", email);
		task.put("authid", authid);
		task.put("timestamp", new Date());
		if (studyid != null) {
			task.put("studyid", studyid);
		}
		db.getCollection("tasks").insert(task);
	}

	public void pushTaskObject(Task task) {
		DBObject obj = new BasicDBObject();
		obj.put("email", task.email);
		obj.put("authid", task.authid);
		obj.put("timestamp", task.timestamp);
		db.getCollection("tasks").insert(obj);
	}

	public List<EmailData> getEmailsForUser(String email, String studyid)
			throws IOException {
		State state = getState(email, studyid);
		if (state == null) {
			return null;
		}
		List<EmailData> allEmails = new ArrayList<>();
		for (int i = 0; i <= state.version; i++) {
			List<EmailData> emails = getEmails(email, studyid, i);
			if (emails == null) {
				return null;
			}
			allEmails.addAll(emails);
		}
		Collections.sort(allEmails, new Comparator<EmailData>() {
			@Override
			public int compare(EmailData o1, EmailData o2) {
				if (o1.timestamp.getTime() < o2.timestamp.getTime()) {
					return -1;
				} else if (o1.timestamp.getTime() > o2.timestamp.getTime()) {
					return 1;
				} else {
					return 0;
				}
			}
		});
		return allEmails;
	}

	public List<EmailData> getEmails(String email, String studyid, int version)
			throws IOException {
		Date now = new Date();
		@SuppressWarnings("deprecation")
		Date gmailLaunch = new Date(2004, 4, 1);
		DBObject query = new BasicDBObject();
		query.put("email", email);
		query.put("version", version);
		if (studyid != null) {
			query.put("studyid", studyid);
		} else {
			query.put("studyid", new BasicDBObject("$exists", false));
		}
		DBObject obj = db.getCollection("emails").findOne(query);
		if (obj == null) {
			return null;
		}
		byte[] contents = (byte[]) obj.get("contents");
		GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(
				contents));
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				stream, "UTF8"));
		JsonParser parser = new JsonParser();
		JsonArray array = parser.parse(reader).getAsJsonArray();
		List<EmailData> resultList = new ArrayList<EmailData>();
		for (JsonElement elem : array) {
			if (elem.isJsonArray()) {
				JsonArray innerArray = elem.getAsJsonArray();
				String response = innerArray.get(0).getAsString() + "\r\n"
						+ innerArray.get(1).getAsString();
				try {
					EmailData emailData = GmailMetadataReader
							.parseEmailFromResponse(response);
					if (emailData != null && emailData.timestamp.before(now)
							&& emailData.timestamp.after(gmailLaunch)) {
						resultList.add(emailData);
					}
				} catch (Exception ex) {
					continue;
				}
			} else {
				try {
					EmailData emailData = gson.fromJson(elem, EmailData.class);
					if (emailData != null) {
						resultList.add(emailData);
					}
				} catch (JsonSyntaxException ex) {
					continue;
				}

			}
		}
		return resultList;
	}

	public void storeEmails(String email, String studyid, int version,
			List<EmailData> emails) throws IOException {
		String json = gson.toJson(emails);
		ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
		GZIPOutputStream stream = new GZIPOutputStream(byteArray);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				stream, "UTF8"));
		writer.write(json);
		writer.close();
		byte[] contents = byteArray.toByteArray();
		BasicDBObject result = new BasicDBObject("email", email)
				.append("version", version).append("contents", contents)
				.append("length", contents.length)
				.append("timestamp", new Date().getTime() / 1000);
		if (studyid != null) {
			result.append("studyid", studyid);
		}
		BasicDBObject query = new BasicDBObject("email", email).append(
				"version", version);
		if (studyid != null) {
			query.put("studyid", studyid);
		} else {
			query.put("studyid", new BasicDBObject("$exists", false));
		}
		db.getCollection("emails").update(query, result, true, false);
	}

	public JsonElement toJsonTree(Object obj) {
		return gson.toJsonTree(obj);
	}

	public JsonArray getEmailsJson(String email, String studyid, int version)
			throws IOException {
		DBObject query = new BasicDBObject();
		query.put("email", email);
		query.put("version", version);
		if (studyid != null) {
			query.put("studyid", studyid);
		} else {
			query.put("studyid", new BasicDBObject("$exists", false));
		}
		DBObject obj = db.getCollection("emails").findOne(query);
		if (obj == null) {
			return null;
		}
		byte[] contents = (byte[]) obj.get("contents");
		GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(
				contents));
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				stream, "UTF8"));
		JsonParser parser = new JsonParser();
		return parser.parse(reader).getAsJsonArray();
	}

	public void storeEmailsJson(String email, String studyid, int version,
			JsonArray emails) throws IOException {
		String json = emails.toString();
		ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
		GZIPOutputStream stream = new GZIPOutputStream(byteArray);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				stream, "UTF8"));
		writer.write(json);
		writer.close();
		byte[] contents = byteArray.toByteArray();
		DBObject result = new BasicDBObject("email", email)
				.append("version", version).append("contents", contents)
				.append("length", contents.length)
				.append("timestamp", new Date().getTime() / 1000);
		if (studyid != null) {
			result.put("studyid", studyid);
		}
		BasicDBObject query = new BasicDBObject("email", email).append(
				"version", version);
		if (studyid != null) {
			query.put("studyid", studyid);
		} else {
			query.put("studyid", new BasicDBObject("$exists", false));
		}
		db.getCollection("emails").update(query, result, true, false);
	}

	public Task popTask() {
		DBObject sort = new BasicDBObject("timestamp", 1);
		DBObject obj = db.getCollection("tasks").findAndModify(null, null,
				sort, true, null, false, false);
		if (obj == null) {
			return null;
		}
		String authid = null;
		if (obj.containsField("authid") && obj.get("authid") != null) {
			authid = obj.get("authid").toString();
		}
		String studyid = null;
		if (obj.containsField("studyid")) {
			studyid = obj.get("studyid").toString();
		}
		return new Task(obj.get("email").toString(), studyid, authid,
				(Date) obj.get("timestamp"));

	}

	public void storeState(State state) {
		DBObject obj = new BasicDBObject();
		obj.put("email", state.email);
		obj.put("lastuid", state.lastuid);
		obj.put("credentials", state.credentials);
		obj.put("version", state.version);
		obj.put("working", state.working);
		obj.put("imap", state.imap);
		if (state.studyid != null) {
			obj.put("studyid", state.studyid);
		}
		DBObject infoObj = new BasicDBObject();
		infoObj.put("name", state.userinfo.name);
		infoObj.put("given_name", state.userinfo.givenName);
		infoObj.put("family_name", state.userinfo.familyName);
		infoObj.put("email", state.userinfo.email);
		obj.put("userinfo", infoObj);
		DBObject query = new BasicDBObject("email", state.email);
		if (state.studyid == null) {
			query.put("studyid", new BasicDBObject("$exists", false));
		} else {
			query.put("studyid", state.studyid);
		}
		db.getCollection("states").update(query, obj, true, false);
	}

	public State getState(String email, String studyid) {
		DBObject query = new BasicDBObject("email", email);
		if (studyid == null) {
			query.put("studyid", new BasicDBObject("$exists", false));
		} else {
			query.put("studyid", studyid);
		}
		DBObject obj = db.getCollection("states").findOne(query);
		if (obj == null) {
			return null;
		}
		int version = (int) obj.get("version");
		String lastuid = obj.get("lastuid").toString();
		String credentials = null;
		if (obj.get("credentials") != null) {
			credentials = obj.get("credentials").toString();
		}
		UserInfo userInfo = null;
		if (obj.containsField("userinfo")) {
			DBObject infoObj = (DBObject) obj.get("userinfo");
			// handle inconsistency in the database
			String name = "";
			String givenName = "";
			String familyName = "";
			if (infoObj.containsField("name")) {
				name = infoObj.get("name").toString();
			}
			if (infoObj.containsField("given_name")) {
				givenName = infoObj.get("given_name").toString();
			}
			if (infoObj.containsField("family_name")) {
				familyName = infoObj.get("family_name").toString();
			}
			// end handle inconsistency in the db
			userInfo = new State.UserInfo(name, givenName, familyName, infoObj
					.get("email").toString());
		}

		boolean imap = obj.containsField("imap")
				&& ((boolean) obj.get("imap")) == true;
		boolean working = obj.containsField("working")
				&& ((boolean) obj.get("working")) == true;
		return new State(email, studyid, version, lastuid, credentials, imap,
				working, userInfo);
	}

	public void updateAuthRequest(String authid, AuthRequest authRequest) {
		DBObject obj = new BasicDBObject();
		obj.put("email", authRequest.email);
		obj.put("username", authRequest.username);
		obj.put("password", authRequest.password);
		obj.put("status", authRequest.status.getValue());
		obj.put("emailType", authRequest.emailType.getValue());
		if (authRequest.studyid != null) {
			obj.put("studyid", authRequest.studyid);
		}
		db.getCollection("authrequests").update(
				new BasicDBObject("_id", new ObjectId(authid)), obj);
	}

	public void log(String email, EmailType emailType, String module,
			String level, String msg) {
		DBObject obj = new BasicDBObject();
		obj.put("email", email);
		obj.put("emailType", emailType.getValue());
		obj.put("module", module);
		obj.put("msg", msg);
		obj.put("level", level);
		Date now = new Date();
		obj.put("timestamp", now);
		db.getCollection("logs").insert(obj);
		System.out.println(now + " | " + level + " | " + email + " | "
				+ emailType + " | " + module + " | " + msg);
	}

	public void logExc(String email, EmailType emailType, String module,
			Exception ex) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ex.printStackTrace(pw);
		String msg = sw.toString(); // stack trace as a string
		log(email, emailType, module, LogLevel.ERROR, msg);
	}

	public Iterable<State> getStates(DBObject query) {
		final DBCursor cursor = db.getCollection("states").find(query);
		return new Iterable<State>() {

			public Iterator<State> iterator() {
				return new Iterator<State>() {

					public boolean hasNext() {
						return cursor.hasNext();
					}

					public State next() {
						DBObject obj = cursor.next();
						int version = (int) obj.get("version");
						String email = (String) obj.get("email");
						String lastuid = obj.get("lastuid").toString();
						String studyid = null;
						if (obj.containsField("studyid")) {
							studyid = obj.get("studyid").toString();
						}
						String credentials = null;
						if (obj.get("credentials") != null) {
							credentials = obj.get("credentials").toString();
						}
						UserInfo userInfo = null;
						if (obj.containsField("userinfo")) {
							DBObject infoObj = (DBObject) obj.get("userinfo");
							// handle inconsistency in the database
							String name = "";
							String givenName = "";
							String familyName = "";
							if (infoObj.containsField("name")) {
								name = infoObj.get("name").toString();
							}
							if (infoObj.containsField("given_name")) {
								givenName = infoObj.get("given_name")
										.toString();
							}
							if (infoObj.containsField("family_name")) {
								familyName = infoObj.get("family_name")
										.toString();
							}
							// end handle inconsistency in the db
							userInfo = new State.UserInfo(name, givenName,
									familyName, infoObj.get("email").toString());
						}

						boolean imap = obj.containsField("imap")
								&& ((boolean) obj.get("imap")) == true;
						boolean working = obj.containsField("working")
								&& ((boolean) obj.get("working")) == true;
						return new State(email, studyid, version, lastuid,
								credentials, imap, working, userInfo);
					}

					public void remove() {
						throw new NotImplementedException();
					}
				};
			}
		};
	}
}
