package edu.mit.media.immersion.db;

public class State {
	public static class UserInfo {
		public String name;
		public String givenName;
		public String familyName;
		public String email;

		public UserInfo(String name, String givenName, String familyName,
				String email) {
			this.name = name;
			this.givenName = givenName;
			this.familyName = familyName;
			this.email = email;
		}
	}

	public String email;
	public int version;
	public String studyid;
	public String lastuid;
	public String credentials;
	public boolean imap = false;
	public boolean working = false;
	public UserInfo userinfo = null;

	public State(String email, String studyid, int version, String lastuid,
			String credentials, boolean imap, boolean working, UserInfo userinfo) {
		super();
		this.studyid = studyid;
		this.email = email;
		this.version = version;
		this.lastuid = lastuid;
		this.userinfo = userinfo;
		this.credentials = credentials;
		this.imap = imap;
		this.working = working;
	}

}
