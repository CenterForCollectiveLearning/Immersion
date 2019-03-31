package edu.mit.media.immersion.reader;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import microsoft.exchange.webservices.data.AutodiscoverLocalException;
import microsoft.exchange.webservices.data.BasePropertySet;
import microsoft.exchange.webservices.data.EmailAddress;
import microsoft.exchange.webservices.data.EmailAddressCollection;
import microsoft.exchange.webservices.data.EmailMessage;
import microsoft.exchange.webservices.data.EmailMessageSchema;
import microsoft.exchange.webservices.data.ExchangeCredentials;
import microsoft.exchange.webservices.data.ExchangeService;
import microsoft.exchange.webservices.data.Folder;
import microsoft.exchange.webservices.data.FolderSchema;
import microsoft.exchange.webservices.data.FolderView;
import microsoft.exchange.webservices.data.GetItemResponse;
import microsoft.exchange.webservices.data.IAutodiscoverRedirectionUrl;
import microsoft.exchange.webservices.data.Item;
import microsoft.exchange.webservices.data.ItemId;
import microsoft.exchange.webservices.data.ItemSchema;
import microsoft.exchange.webservices.data.ItemView;
import microsoft.exchange.webservices.data.NameResolutionCollection;
import microsoft.exchange.webservices.data.PropertySet;
import microsoft.exchange.webservices.data.SearchFilter;
import microsoft.exchange.webservices.data.SearchFolder;
import microsoft.exchange.webservices.data.SearchFolderTraversal;
import microsoft.exchange.webservices.data.ServiceResponseCollection;
import microsoft.exchange.webservices.data.SortDirection;
import microsoft.exchange.webservices.data.WebCredentials;
import microsoft.exchange.webservices.data.WellKnownFolderName;
import edu.mit.media.immersion.EmailData;

public class ExchangeMetadataReader implements IMetadataReader {
	private static final String SEARCH_FOLDER_NAME = "Immersion.media";
	private String username;
	private String password;
	private String email;
	private ExchangeService service = null;

	public ExchangeMetadataReader(String username, String password, String email)
			throws Exception {
		// turn off logging below WARNING level
		LogManager.getLogManager().getLogger("").setLevel(Level.WARNING);

		this.username = username;
		this.password = password;
		this.email = email;
		// connect to Exchange Server
		this.service = new ExchangeService();
		ExchangeCredentials credentials = new WebCredentials(this.username,
				this.password);
		service.setCredentials(credentials);
		// allow redirections from auto-discover service
		service.autodiscoverUrl(this.email, new IAutodiscoverRedirectionUrl() {
			@Override
			public boolean autodiscoverRedirectionUrlValidationCallback(
					String redirectionUrl) throws AutodiscoverLocalException {
				return true;
			}
		});
	}

	@Override
	public String getDisplayName() {
		try {
			NameResolutionCollection col = this.service.resolveName(this.email,
					null, true);
			return col.nameResolutionCollection(0).getContact()
					.getDisplayName();
		} catch (Exception e) {
			return "";
		}
	}

	@Override
	public MetadataResult readEmailsMetadata(String checkpoint, int nEmails)
			throws Exception {
		// prepare the result object
		MetadataResult result = new MetadataResult();
		result.checkpoint = checkpoint;
		List<EmailData> emails = new ArrayList<EmailData>();
		result.emails = emails;

		// find the Immersion search folder or create a new one
		SearchFolder searchFolder = null;
		List<Folder> folders = service.findFolders(
				WellKnownFolderName.Root,
				new SearchFilter.IsEqualTo(FolderSchema.DisplayName,
						SEARCH_FOLDER_NAME), new FolderView(1)).getFolders();
		if (folders.isEmpty()) {
			// create a new one
			searchFolder = new SearchFolder(service);
			searchFolder.getSearchParameters().getRootFolderIds()
					.add(WellKnownFolderName.MsgFolderRoot);
			searchFolder.getSearchParameters().setTraversal(
					SearchFolderTraversal.Deep);
			searchFolder.getSearchParameters()
					.setSearchFilter(
							new SearchFilter.IsEqualTo(ItemSchema.ItemClass,
									"IPM.Note"));
			searchFolder.setDisplayName(SEARCH_FOLDER_NAME);
			searchFolder.save(WellKnownFolderName.Root);
			// allow some time for the folder to re-index
			Thread.sleep(5000);
			searchFolder = SearchFolder.bind(service, searchFolder.getId());
		} else {
			searchFolder = (SearchFolder) folders.get(0);
		}
		// fetch e-mails newer than checkpoint by increasing DateTimeCreated
		// order
		Date last = new Date(Long.parseLong(checkpoint));
		ItemView view = new ItemView(nEmails + 1);
		PropertySet set = new PropertySet(BasePropertySet.IdOnly);
		// set.add(ItemSchema.DateTimeCreated);
		view.setPropertySet(set);
		view.getOrderBy().add(ItemSchema.DateTimeCreated,
				SortDirection.Ascending);
		List<Item> findResults = searchFolder
				.findItems(
						new SearchFilter.IsGreaterThan(
								ItemSchema.DateTimeCreated, last), view)
				.getItems();
		List<ItemId> itemIds = new ArrayList<ItemId>();
		for (Item item : findResults) {
			itemIds.add(item.getId());
		}
		if (itemIds.size() == 0) {
			return result;
		}
		set = new PropertySet(BasePropertySet.IdOnly);
		set.add(EmailMessageSchema.From);
		set.add(EmailMessageSchema.ToRecipients);
		set.add(EmailMessageSchema.CcRecipients);
		set.add(ItemSchema.DateTimeReceived);
		set.add(ItemSchema.ConversationId);
		set.add(ItemSchema.DateTimeCreated);
		ServiceResponseCollection<GetItemResponse> responses = service
				.bindToItems(itemIds, set);
		for (GetItemResponse response : responses) {
			try {
				Item item = response.getItem();
				if (item == null || item.getDateTimeCreated().equals(last)) {
					continue;
				}
				result.checkpoint = Long.toString(item.getDateTimeCreated()
						.getTime());
				EmailMessage msg = null;
				try {
					msg = (EmailMessage) item;
				} catch (ClassCastException ex) {
					continue;
				}
				// fill the email metadata object
				EmailData email = new EmailData();
				EmailAddress fromAddr = msg.getFrom();
				email.from = new InternetAddress("\"" + fromAddr.getName()
						+ "\"" + " <" + fromAddr.getAddress() + ">");
				email.to = new ArrayList<InternetAddress>();
				// combine TO and CC into one field
				EmailAddressCollection col = msg.getToRecipients();
				col.addEmailRange(msg.getCcRecipients().iterator());
				for (EmailAddress toAddr : col) {
					try {
						email.to.add(new InternetAddress("\""
								+ toAddr.getName() + "\"" + " <"
								+ toAddr.getAddress() + ">"));
					} catch (AddressException ex) {
						continue;
					}
				}
				email.timestamp = msg.getDateTimeReceived();
				email.thrid = msg.getConversationId().toString();
				email.isSent = msg.getFrom().getAddress().toLowerCase()
						.equals(this.email.toLowerCase());
				emails.add(email);
			} catch (Exception ex) {
				continue;
			}
		}
		return result;
	}
}
