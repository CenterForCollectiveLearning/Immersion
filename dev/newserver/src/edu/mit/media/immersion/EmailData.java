package edu.mit.media.immersion;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import javax.mail.internet.InternetAddress;

import com.google.gson.annotations.SerializedName;

public class EmailData implements Serializable {

	private static final long serialVersionUID = 1L;

	public String body;
	
	@SerializedName("subject")
	public String subject;
	@SerializedName("fromField")
	public InternetAddress from;
	@SerializedName("toField")
	public List<InternetAddress> to;
	@SerializedName("dateField")
	public Date timestamp;
	@SerializedName("threadid")
	public String thrid;
	@SerializedName("isSent")
	public boolean isSent = false;
	public String UID;
	public boolean auto;
}
