package edu.mit.media.immersion.db;

public class AuthRequest {
	public AuthStatus status;
	public String email;
	public String password;
	public EmailType emailType;
	public String username;
	public String studyid;

	public AuthRequest(String email, String username, String password,
			EmailType emailType, AuthStatus status, String studyid) {
		this.email = email;
		this.username = username;
		this.password = password;
		this.emailType = emailType;
		this.status = status;
		this.studyid = studyid;
	}
}
