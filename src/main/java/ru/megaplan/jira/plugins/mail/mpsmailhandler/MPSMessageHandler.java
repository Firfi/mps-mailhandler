package ru.megaplan.jira.plugins.mail.mpsmailhandler;

import com.atlassian.core.AtlassianCoreException;
import com.atlassian.core.user.preferences.Preferences;
import com.atlassian.core.util.RandomGenerator;
import com.atlassian.crowd.embedded.api.CrowdService;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.user.UserEventType;
import com.atlassian.jira.exception.AddException;
import com.atlassian.jira.exception.PermissionException;
import com.atlassian.jira.exception.RemoveException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.history.ChangeItemBean;
import com.atlassian.jira.notification.NotificationRecipient;
import com.atlassian.jira.plugins.mail.handlers.CreateIssueHandler;
import com.atlassian.jira.plugins.mail.handlers.CreateOrCommentHandler;
import com.atlassian.jira.plugins.mail.handlers.FullCommentHandler;
import com.atlassian.jira.plugins.mail.handlers.NonQuotedCommentHandler;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.service.util.ServiceUtils;
import com.atlassian.jira.service.util.handler.MessageHandlerContext;
import com.atlassian.jira.service.util.handler.MessageHandlerErrorCollector;
import com.atlassian.jira.service.util.handler.MessageUserProcessor;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.jira.user.preferences.PreferenceKeys;
import com.atlassian.jira.user.preferences.UserPreferencesManager;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.web.util.FileNameCharacterCheckerUtil;
import com.atlassian.mail.Email;
import com.atlassian.mail.MailException;
import com.atlassian.mail.MailFactory;
import com.atlassian.mail.MailUtils;
import com.atlassian.mail.queue.AbstractMailQueueItem;
import com.atlassian.mail.server.SMTPMailServer;
import com.atlassian.util.concurrent.Nullable;
import com.opensymphony.util.TextUtils;
import com.sun.mail.imap.IMAPMessage;
import org.apache.log4j.Logger;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.message.MessageSubjectWrapper;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.util.MessageProxyFrom;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.util.NotJiraMailUtils;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: i.loskutov
 * Date: 01.06.12
 * Time: 17:10
 * To change this template use File | Settings | File Templates.
 */
public class MPSMessageHandler extends CreateOrCommentHandler {

    private static Logger log = Logger.getLogger(MPSMessageHandler.class);

    private static final String adminUserName = "megaplan";
    private static final String FALSE = "false";
    private static final String UTIDOMAIN = "utinet.ru";
    public static final String MEGADOMAIN = "megaplan.ru";
    private static final String MEGARESPONSE = "noreply@megaplan.ru";
    private static final String UNDELIEVERED = "Undelivered Mail Returned to Sender";
    private static final String EMAIL_ACCOUNTS_GROUP = "email-accounts";

    private static final FileNameCharacterCheckerUtil fileNameCharacterCheckerUtil = new FileNameCharacterCheckerUtil();
    private static final char INVALID_CHAR_REPLACEMENT = '_';



    @Override
    public boolean handleMessage(Message message, MessageHandlerContext context)
            throws MessagingException
    {
        boolean doDelete = false;

        try {

            log.warn("received some message : " + message.getSubject());


            String subject = message.getSubject();
            if (subject == null || subject.trim().length() == 0) {
                subject = ComponentAccessor.getI18nHelperFactory().getInstance(Locale.getDefault()).getText("ru.megaplan.jira.plugins.mail.mpsmailhandler.withoutsubject");
                if (subject == null || subject.trim().length() == 0) subject = "<Without subject>";
                message = forceSetSubjectByProxy(message,subject);
            }

            if (sanitizeSubject(subject, UNDELIEVERED) && sanitizeAuthor(message.getFrom(), UTIDOMAIN, MEGADOMAIN))
            {
                log.debug("subject sanitized : " + subject + " with author : " + message.getFrom());
                return true;
            }
            if (sanitizeAuthor(message.getFrom(), MEGARESPONSE)) {
                log.debug("author sanitized : " + message.getFrom() + " with subject : " + subject);
                return true;
            }

            User reporter = getReporter(message, context);

            if (reporter == null) {
                final String error = "can't find reporter in email message : " + message.getSubject();
                context.getMonitor().warning(error);
                context.getMonitor().messageRejected(message, error);
                return true;
            }

            if (!canHandleMessage(message, context.getMonitor()))
            {

                log.info("Cannot handle message '" + subject + "'." + (deleteEmail ? " deleting it" : ""));
                return false;
            }

            if (log.isDebugEnabled())
            {
                log.debug("Looking for Issue Key in subject '" + subject + "'.");
            }

            Issue issue = ServiceUtils.findIssueObjectInString(subject);

            if (issue == null)
            {
                // If we cannot find the issue from the subject of the e-mail message
                // try finding the issue using the in-reply-to message id of the e-mail message
                log.debug("Issue Key not found in subject '" + subject + "'. Inspecting the in-reply-to message ID.");
                issue = getAssociatedIssue(message);
            }

            // if we have found an associated issue
            if (issue != null)
            {
                //add the message as a comment to the issue
                if ((stripquotes == null) || FALSE.equalsIgnoreCase(stripquotes)) //if stripquotes not defined in setup
                {
                    FullCommentHandler fc = new FullCommentHandler()
                    {

                        @Override
                        protected MessageUserProcessor getMessageUserProcessor()
                        {
                            return MPSMessageHandler.this.getMessageUserProcessor();
                        }
                        @Override
                        protected User getReporter(final Message message, MessageHandlerContext context) throws MessagingException {
                            return MPSMessageHandler.this.getReporter(message, context);
                        }
                    };

                    fc.init(params, context.getMonitor());
                    doDelete = fc.handleMessage(message, context); //get message with quotes
                }
                else
                {
                    NonQuotedCommentHandler nq = new MPSNonQuotedCommentHandler()
                    {
                        @Override
                        protected MessageUserProcessor getMessageUserProcessor()
                        {
                            return MPSMessageHandler.this.getMessageUserProcessor();
                        }
                        @Override
                        protected User getReporter(final Message message, MessageHandlerContext context) throws MessagingException {
                            return MPSMessageHandler.this.getReporter(message, context);
                        }

                        @Override
                        protected String getEmailBody(Message message) throws MessagingException
                        {
                            String filteredBody = NotJiraMailUtils.getBody(message);
                            String stripped = stripQuotedLines(filteredBody, NotJiraMailUtils.isGmail(message));
                            return stripped;
                        }




                    };

                    nq.init(params, context.getMonitor());
                    doDelete = nq.handleMessage(message, context); //get message without quotes
                }

            }
            else
            { //no issue found, so create new issue in default project
                if (log.isDebugEnabled())
                {
                    log.debug("No Issue found for email '" + subject + "' - creating a new Issue.");
                }

                boolean isXml = false;
                try {
                    isXml = (CreateMPIssueHandler.accountXmlIsAttached(message) >= 0);
                } catch (UnsupportedEncodingException e) {
                    log.warn("aquired some freak message", e);
                    return true;
                } catch (MessagingException e) {
                    if ("Unable to load BODYSTRUCTURE".equals(e.getMessage())) {
                        log.debug("sort of spam...");
                        return true; //delete shit
                    }
                    throw e;
                }


                if (isXml) {
                    log.debug("creating MPSXML");
                    CreateIssueHandler mpCreateHandler = new CreateMPIssueHandler() {
                        @Override
                        protected MessageUserProcessor getMessageUserProcessor()
                        {
                            return MPSMessageHandler.this.getMessageUserProcessor();
                        }
                    };
                    mpCreateHandler.init(params,context.getMonitor());
                    doDelete = mpCreateHandler.handleMessage(message,context);
                } else {
                    CreateIssueHandler createIssueHandler = new CreateIssueHandlerExtended()
                    {
                        @Override
                        protected MessageUserProcessor getMessageUserProcessor()
                        {
                            return MPSMessageHandler.this.getMessageUserProcessor();
                        }
                        @Override
                        protected User getReporter(final Message message, MessageHandlerContext context) throws MessagingException {
                            return MPSMessageHandler.this.getReporter(message, context);
                        }


                    };
                    createIssueHandler.init(params, context.getMonitor());
                    doDelete = createIssueHandler.handleMessage(message, context);
                    if (!doDelete) {
                        try {
                            checkForEncodingAnomaly(message, null);
                        } catch (IOException e) {
                            log.error(e.getMessage().startsWith("Unknown encoding"));
                            if (e.getMessage() != null && e.getMessage().startsWith("Unknown encoding")) {
                                String foundError = "we found a rare encoding anomaly! for message : " + message.getSubject() + ". hooray. FUCKI'N DELETE IT";
                                log.error(foundError);
                                context.getMonitor().warning("foundError",e);
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("exception in mail handling:", e);
            context.getMonitor().warning(getI18nBean().getText("admin.mail.unable.to.create.issue"), e);
        }
        return doDelete;
    }



    private void checkForEncodingAnomaly(Message message, Multipart multipart) throws MessagingException, IOException {
        if (multipart == null) {
            Object multipartLi = message.getContent();
            if (multipartLi instanceof Multipart) {
                checkForEncodingAnomaly(message, (Multipart) multipartLi);
            }
        } else {
            for (int i = 0, n = multipart.getCount(); i < n; i++) {
                Object multipartLi = multipart.getBodyPart(i).getContent();
                if (multipartLi instanceof Multipart) {
                    checkForEncodingAnomaly(message, (Multipart) multipartLi);
                }
            }
        }
    }


    private Message forceSetSubjectByProxy(final Message message, final String subject) {
        Message m = new MessageSubjectWrapper(message,subject);
        return m;
    }

    private boolean isMegaplanMessage(Message message) {
        return false;
    }

    public static boolean sanitizeAuthor(Address[] from, String... domains) {
        if (from == null || from.length == 0) return false;
        for (Address address : from) {
            if (!(address instanceof InternetAddress)) continue;
            InternetAddress fromAddress = (InternetAddress) address;
            String stringAddress = fromAddress.getAddress();
            for (String domain : domains) {
                if (stringAddress.endsWith(domain)) return true;
            }
        }
        return false;
    }

    private boolean sanitizeSubject(String subject, String compare) {
        if (compare.equals(subject)) return true;
        return false;
    }

    @Override
    public void init(final Map<String, String> params, MessageHandlerErrorCollector errorCollector) {
        super.init(params, errorCollector);
    }



    @Override
    protected User createUserForReporter(final Message message, MessageHandlerContext context) {
        User u = null;
        try {
            u = createUserDebug(message, context);
            log.warn("creating user : " + u);
            if (u == null) {
                try {
                    final String error = "can't find reporter in email message : " + message.getSubject();
                    context.getMonitor().warning(error);
                    Address[] ass = message.getFrom();
                    Address a = (ass.length > 0? ass[0]: null);
                    log.warn("corrupted address is : " + a);
                    if (a != null && a instanceof InternetAddress) {
                        String addr = ((InternetAddress) a).getAddress();
                        User errorReporter = ComponentAccessor.getUserManager().getUser(addr);
                        log.warn("reporter for corrupted address " + addr + " is " + errorReporter);
                        if (errorReporter != null) {
                            removeUser(errorReporter);
                        }
                    }
                    throw new NullPointerException("created user is null");
                } catch (MessagingException e) {
                    throw new NullPointerException("created user is null" + " and more error : " + e.getMessage());
                }
            } else {
                if (context.isRealRun()) {
                    addEmailGroup(u);
                    addProperties(u);
                    checkValidity(u, message, context);
                }
            }
        } catch (RuntimeException e) {
            String error = "error occured creating user : " + u;
            log.error(error);
            throw e;
        }


        return u;
    }

    protected User createUserDebug(final Message message, MessageHandlerContext context)
    {
        User reporter = null;
        log.warn("createuserdebug");
        try
        {
            if (!userManager.hasWritableDirectory())
            {
                context.getMonitor().warning("Unable to create user for reporter because no user directories are writable.");
                return null;
            }

            // If reporter is not a recognised user, then create one from the information in the e-mail
            log.debug("Cannot find reporter for message. Creating new user.");

            final Address[] senders = message.getFrom();
            if (senders == null || senders.length == 0)
            {
                context.getMonitor().error("Cannot retrieve sender information from the message.");
                return null;
            }
            final InternetAddress internetAddress = (InternetAddress) senders[0];
            final String reporterEmail = internetAddress.getAddress();
            if (!TextUtils.verifyEmail(reporterEmail))
            {
                context.getMonitor().error("The email address [" + reporterEmail + "] received was not valid. Ensure that your mail client specified a valid 'From:' mail header. (see JRA-12203)");
                return null;
            }
            String fullName = internetAddress.getPersonal();
            if ((fullName == null) || (fullName.trim().length() == 0))
            {
                fullName = reporterEmail;
            }

            final String password = RandomGenerator.randomPassword();
            log.warn("creating reporter");
            if (notifyUsers)
            {
                reporter = context.createUser(reporterEmail, password, reporterEmail, fullName, UserEventType.USER_CREATED);
            }
            else
            {
                reporter = context.createUser(reporterEmail, password, reporterEmail, fullName, null);
            }
            log.warn("reporter created: " + reporter);
            if (context.isRealRun())
            {
                log.debug("Created user " + reporterEmail + " as reporter of email-based issue.");
            }
        }
        catch (final Exception e)
        {
            context.getMonitor().error("Error occurred while automatically creating a new user from email", e);
            log.warn("create exception", e);
        }
        return reporter;
    }


    public static void addProperties(User u) {
        UserPreferencesManager userPreferencesManager = ComponentAccessor.getUserPreferencesManager();
        Preferences preferences = userPreferencesManager.getPreferences(u);
        try {
            preferences.setBoolean(PreferenceKeys.USER_AUTOWATCH_DISABLED, true);
            preferences.setBoolean(PreferenceKeys.USER_NOTIFY_OWN_CHANGES, false);
            preferences.setString(PreferenceKeys.USER_NOTIFICATIONS_MIMETYPE, NotificationRecipient.MIMETYPE_HTML);
        } catch (AtlassianCoreException e) {
            log.error("can't set issue autowatch so user will get jira notifications, shame", e);
        }
    }

    public static void checkValidity(User u, Message message, MessageHandlerContext context) {
        GroupManager gm = ComponentAccessor.getGroupManager();
        log.warn("user groups : " + Arrays.toString(gm.getGroupNamesForUser(u).toArray()));
        if (!gm.isUserInGroup(u.getName(), EMAIL_ACCOUNTS_GROUP)) {
            String error = "Can't add user " + u.getName() + " in group " + EMAIL_ACCOUNTS_GROUP;
            log.error(error);
            context.getMonitor().error(error);
            context.getMonitor().messageRejected(message, error);
            final Email finalEmail = new Email("igor.loskutoff@gmail.com");
            finalEmail.setBody("SCARY SHIT HAPPENED " + error);
            ComponentAccessor.getMailQueue().addItem(new AbstractMailQueueItem() {
                @Override
                public void send() throws MailException {
                    incrementSendCount();

                    SMTPMailServer smtpMailServer = MailFactory.getServerManager().getDefaultSMTPMailServer();

                    if (smtpMailServer == null) {
                        return;
                    }

                    // Check if mailing is disabled && if SMTPMailServer has been set
                    if (!MailFactory.isSendingDisabled()){
                        // If not, send the message
                        if (mailThreader != null) mailThreader.threadEmail(finalEmail);
                        log.warn("sending warning email");
                        smtpMailServer.send(finalEmail);
                        if (mailThreader != null) mailThreader.storeSentEmail(finalEmail);
                    }
                }
            });
            removeUser(u);
        }
    }

    public static void removeUser(@Nullable User u) {
        if (u != null)
            ComponentAccessor.getUserUtil().removeUser(ComponentAccessor.getUserManager().getUser(adminUserName), u);
    }

    public static void addEmailGroup(User user) {
        log.warn("adding email group : " + user);
        GroupManager gm = ComponentAccessor.getGroupManager();
        Collection<Group> gs = gm.getGroupsForUser(user);
        UserUtil ui =  ComponentAccessor.getUserUtil();
        Group emailAccounts = gm.getGroupObject(EMAIL_ACCOUNTS_GROUP);
        if (emailAccounts == null) log.error("group "+EMAIL_ACCOUNTS_GROUP+" is not exist");
        for (Group g : gs) {
            try {
                log.warn("removing user : " + user.getName() + " from group : " + g.getName());
                ui.removeUserFromGroup(g, user);
            } catch (PermissionException e) {
                log.error("permission exception",e);
                e.printStackTrace();
            } catch (RemoveException e) {
                log.error("remove exception",e);
                e.printStackTrace();
            }
        }
        try {
            log.warn("adding user : " + user.getName() + " to group : " + emailAccounts.getName());
            ui.addUserToGroup(emailAccounts, user);
        } catch (PermissionException e) {
            e.printStackTrace();
        } catch (AddException e) {
            e.printStackTrace();
        }
    }

}
