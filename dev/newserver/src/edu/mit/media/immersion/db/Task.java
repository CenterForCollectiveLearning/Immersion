package edu.mit.media.immersion.db;

import java.util.Date;

public class Task {
	public Task(String email, String studyid, String authid, Date timestamp) {
		this.email = email;
		this.studyid = studyid;
		this.authid = authid;
		this.timestamp = timestamp;
	}

	public String email;
	public String authid;
	public Date timestamp;
	public String studyid;
}
