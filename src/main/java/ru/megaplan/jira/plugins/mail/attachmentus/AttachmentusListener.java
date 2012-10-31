package ru.megaplan.jira.plugins.mail.attachmentus;

import com.atlassian.core.ofbiz.util.OFBizPropertyUtils;
import com.atlassian.core.util.map.EasyMap;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.gzipfilter.org.apache.commons.lang.StringEscapeUtils;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.config.util.JiraHome;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.issue.IssueEventManager;
import com.atlassian.jira.event.type.EventType;
import com.atlassian.jira.issue.*;
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.issue.changehistory.ChangeHistoryItem;
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager;
import com.atlassian.jira.issue.comments.Comment;
import com.atlassian.jira.issue.comments.CommentManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.project.ProjectKeys;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.jira.user.preferences.UserPreferencesManager;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.util.JiraKeyUtils;
import com.atlassian.jira.util.VelocityParamFactory;
import com.atlassian.mail.Email;
import com.atlassian.mail.MailException;
import com.atlassian.mail.MailFactory;
import com.atlassian.mail.queue.AbstractMailQueueItem;
import com.atlassian.mail.queue.MailQueue;
import com.atlassian.mail.server.SMTPMailServer;
import com.atlassian.velocity.VelocityManager;
import org.apache.log4j.Logger;
import org.apache.velocity.exception.VelocityException;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util.RegionAssigneesMapper;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.annotation.Nullable;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 13.06.12
 * Time: 11:10
 * To change this template use File | Settings | File Templates.
 */
public class AttachmentusListener implements InitializingBean, DisposableBean {

    private final static String EMAIL_ACCOUNTS = "email-accounts";
    private static final String EMAIL_PATTERN = "^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@" +
            "[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
    private static final String RELATIVE_PATTERN = "^\\..*";
    private static final Pattern RELATIVE_COMPILED_PATTERN = Pattern.compile(RELATIVE_PATTERN);
    private static final Pattern EMAIL_COMPILED_PATTERN = Pattern.compile(EMAIL_PATTERN);
    private static final String USER_FIRST_NAME_PROPERTY = "tellmeyourname.firstname";

    private static final String ISSUE_COMMENTED_SUBJECT_TEMPLATE = "/templates/email/subject/issuecommented_mps.vm";
    private static final String ISSUE_COMMENTED_BODY_TEMPLATE = "/templates/email/text/issuecommented_mps.vm";
    private static final String ISSUE_COMMENTED_BODY_HTML_TEMPLATE = "/templates/email/html/issuecommented_mps.vm";
    private static final String ISSUE_CREATED_SUBJECT_TEMPLATE = "/templates/email/subject/issuecreated_mps.vm";
    private static final String ISSUE_CREATED_BODY_TEMPLATE =  "/templates/email/text/issuecreated_mps.vm";
    private static final String ISSUE_CREATED_BODY_HTML_TEMPLATE =  "/templates/email/html/issuecreated_mps.vm";

    private static final String ISSUE_CREATED_NONWORK_BODY_TEMPLATE = "templates/email/text/issuecreated_mps_nonwork.vm";
    private static final String ISSUE_WORKSTARTED_TEMPLATE = "templates/email/text/issueworkstarted_mps.vm";

    private static final String ATTACHMENT_FIELD_TYPE = "Attachment";
    public static final String A_FILENAME = "filename";
    public static final String A_MIME_TYPE = "mimeType";
    public static final String A_PATH = "path";
    public static final String A_CREATED = "created";
    public static final String GRADE_PROPERTY_KEY_PREFIXED = UserUtil.META_PROPERTY_PREFIX + "MPS_grade";
    public static final String MEGAPLAN_FROM_NAME = "Мегаплан";
    private static final Long LOGINCUSTOMFIELDID = 11261L;
    private final static Long ORIGINAL_SUMMARY_FIELD_ID = 14561L;
    private final static String SERVICE_COMPANY_CUSTOM_FIELD_NAME = "MPS Обслуживающая компания";
    private final static String HOHLY_OPTION_NAME = "Украина";
    private final static String DEFAULT_FROM_EMAIL = "support@megaplan.ru";
    private final static String HOHLY_FROM_EMAIL = "support@megaplan.ua";


    private final static Logger log = Logger.getLogger(AttachmentusListener.class);

    private final VelocityManager velocityManager;
    private final VelocityParamFactory velocityParamFactory;
    private final MailQueue mailQueue;
    private final EventPublisher eventPublisher;
    private final ChangeHistoryManager changeHistoryManager;
    private final AttachmentManager attachmentManager;
    private final ApplicationProperties applicationProperties;
    private final JiraHome jiraHome;
    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final CommentManager commentManager;
    private final JiraKeyUtils jiraKeyUtils;
    private final CustomFieldManager customFieldManager;
    private final UserPropertyManager userPropertyManager;
    private final IssueManager issueManager;
    private final UserPreferencesManager userPreferencesManager;

    private final CustomField serviceCompanyCF;


    AttachmentusListener(VelocityManager velocityManager, VelocityParamFactory velocityParamFactory,
                         MailQueue mailQueue,
                         EventPublisher eventPublisher, ChangeHistoryManager changeHistoryManager,
                         AttachmentManager attachmentManager, ApplicationProperties applicationProperties,
                         JiraHome jiraHome, JiraAuthenticationContext jiraAuthenticationContext,
                         CommentManager commentManager, CustomFieldManager customFieldManager,
                         UserPropertyManager userPropertyManager, IssueManager issueManager, UserPreferencesManager userPreferencesManager) {
        this.velocityManager = velocityManager;
        this.velocityParamFactory = velocityParamFactory;
        this.mailQueue = mailQueue;
        this.eventPublisher = eventPublisher;
        this.changeHistoryManager = changeHistoryManager;
        this.attachmentManager = attachmentManager;
        this.applicationProperties = applicationProperties;
        this.jiraHome = jiraHome;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.commentManager = commentManager;
        this.userPropertyManager = userPropertyManager;
        this.issueManager = issueManager;
        this.userPreferencesManager = userPreferencesManager;
        this.jiraKeyUtils =  new JiraKeyUtils();
        this.customFieldManager = customFieldManager;

        this.serviceCompanyCF = customFieldManager.getCustomFieldObjectByName(SERVICE_COMPANY_CUSTOM_FIELD_NAME);

    }



    @EventListener
    public void issueEvent(final IssueEvent issueEvent) throws IOException, MessagingException, VelocityException {
        boolean isHtml = true;
        boolean isComment = EventType.ISSUE_COMMENTED_ID.equals(issueEvent.getEventTypeId());
        boolean isCreate = EventType.ISSUE_CREATED_ID.equals(issueEvent.getEventTypeId());

        if (!issueEvent.getIssue().getProjectObject().getKey().equals("MPS")) {
            //log.debug("event Project is not \"MPS\", so skip it");
            return;
        }
        if (!isComment && !isCreate)
        {
            //log.debug("Event type is not \"commented\" or not \"created\" but: " + issueEvent.getEventTypeId() + ", so skip it");
            return;
        }

        //boolean isWorkStarted = isWorkStarted(issueEvent);
//        if (sanitizeClientIsReporter(issueEvent.getIssue())) {
//            log.debug("reporter presumably is not our client, so skip it");
//            return;
//        }
        if (!isCreate && sanitizeReporterIsCommentator(issueEvent.getIssue(), issueEvent.getComment())) {
            //log.debug("commentator is reporter, we skip it");
            return;
        }
        if (!isCreate && sanitizeCommentPermissions(issueEvent.getComment())) {
            //log.debug("comment has permission restrictions so we do not sent it to user");
            return;
        }


        List<Map<String,Object>> attachmentsProps = null;
        if (isComment) {
            attachmentsProps = sanitizeHasLastAttach(issueEvent.getIssue(), issueEvent.getComment().getAuthorUser(), isComment);
        }


        Email email = null;
        if (isComment) {
            email = createEmail(issueEvent, attachmentsProps, isHtml, isComment);
        } else if (isCreate) {
//            if (isWorkTime()) {
                email = createEmail(issueEvent, attachmentsProps, isHtml, isComment);
//            } else {
//                email = createEmail(issueEvent, attachmentsProps,ISSUE_CREATED_SUBJECT_TEMPLATE,ISSUE_CREATED_BODY_TEMPLATE); //add nonwork lated
//            }

        }
        if (email == null) {
            log.error("email is null for issue : " + issueEvent.getIssue().getKey());
            log.error("reporter : " + issueEvent.getIssue().getReporter().getName());
            if (issueEvent.getComment() != null) {
                log.error("comment : " + issueEvent.getComment().getBody() + " author : " + issueEvent.getComment().getAuthor());
            }
            throw new IOException("cant' create email do something with it fast");
        }
        email.setEncoding("UTF-8");
        final Email finalEmail = email;
        final Issue issue = issueEvent.getIssue();

        AbstractMailQueueItem item = new AbstractMailQueueItem(email.getSubject()) {

            private final Logger log = Logger.getLogger(AttachmentusListener.class);

            @Override
            public void send() throws MailException
            {
                incrementSendCount();

                SMTPMailServer smtpMailServer = MailFactory.getServerManager().getDefaultSMTPMailServer();

                if (smtpMailServer == null)
                {
                    log.warn("Not sending message as the default SMTP Mail Server is not defined. for issue : " + issue.getKey());
                    return;
                }

                // Check if mailing is disabled && if SMTPMailServer has been set
                if (!MailFactory.isSendingDisabled())
                {
                    // If not, send the message
                    if (mailThreader != null) mailThreader.threadEmail(finalEmail);
                    smtpMailServer.send(finalEmail);
                    if (mailThreader != null) mailThreader.storeSentEmail(finalEmail);
                }
                else
                {
                    log.warn("Not sending message as sending is turned off. for issue " + issue.getKey());
                }
                log.debug("end send email");
            }
        };
        //TODO create changeitem in issue, only after that send issue
        mailQueue.addItem(item);
    }





    public List<Map<String, Object>> sanitizeHasLastAttach(Issue issue, User user, boolean isComment) {
        List<Map<String, Object>> attachmentsProps = new ArrayList<Map<String, Object>>();
        if (user == null || issue == null) return null;
        List<ChangeHistoryItem> changeItems = changeHistoryManager.getAllChangeItems(issue);
        if (changeItems == null || changeItems.size() == 0) return null;
        List<Comment> allCommentsForUser = commentManager.getCommentsForUser(issue, user);
        Iterator<Comment> comIt = allCommentsForUser.iterator();
        while (comIt.hasNext()) {
            User authorUser = comIt.next().getAuthorUser();
            if (authorUser == null || !authorUser.equals(user)) {
                comIt.remove();
            }
        }
        Comment previousComment = null;
        if (allCommentsForUser.size() > (isComment?1:0)) {
            previousComment = allCommentsForUser.get(allCommentsForUser.size() - 1 - (isComment?1:0));
        }
        Date previousCommentDate = (previousComment==null?new Date(1):previousComment.getCreated());
        List<Attachment> attachments = getLastAttachments(changeItems, user, previousCommentDate);
        String attachmentBase = (String) applicationProperties.asMap().get(APKeys.JIRA_PATH_ATTACHMENTS);
        if (attachments.isEmpty()) return attachmentsProps;
        for (int i = attachments.size()-1; i >= 0; --i) {
            Attachment attachment = attachments.get(i);
            String absolute = "";
            if (isRelative(attachmentBase)) absolute = jiraHome.getDataDirectory().getPath()+'/'; //on case sdk enviroment
            String attachmentContextPath = absolute + attachmentBase + '/' + issue.getProjectObject().getKey() +'/' +issue.getKey() + '/' + attachment.getId();
            Map<String, Object> attachmentProp = new HashMap<String, Object>();
            attachmentProp.put(A_PATH, attachmentContextPath);
            attachmentProp.put(A_CREATED, attachment.getCreated());
            attachmentProp.put(A_MIME_TYPE, attachment.getMimetype());
            attachmentProp.put(A_FILENAME, attachment.getFilename());
            attachmentsProps.add(attachmentProp);
        }
        return attachmentsProps;
    }

    /*
     * get all last attachments by this user. do check attachment time
     * @return list of change items from last to first
     */
    private List<Attachment> getLastAttachments(List<ChangeHistoryItem> changeItems, User user, Date lastComment) {
        List<Attachment> result = new ArrayList<Attachment>();
        Set<Long> deletedAttachmentsIds = new HashSet<Long>();
        for (int i = changeItems.size()-1;i >= 0;--i) {
            ChangeHistoryItem lastItem = changeItems.get(i);
            if (lastItem.getCreated().before(lastComment)) {
                log.debug("end of last attachment items sequence : " + lastItem.getCreated() + " (before " + lastComment + ")");
                break;
            }
            if (!user.getName().equals(lastItem.getUser())) continue;
            if (!ATTACHMENT_FIELD_TYPE.equals(lastItem.getField()))  {
                log.debug("field type of last change not equals : " + ATTACHMENT_FIELD_TYPE);
                break;
            }
            if (lastItem.getTos().size() > 1) {
                // tos size ok here
            }
            if (lastItem.getTos().size() == 0) {
                log.warn(lastItem.getFroms());
                for (String key : lastItem.getFroms().keySet()) {
                    deletedAttachmentsIds.add(Long.parseLong(key));
                }
                continue; //it is deleted attachment
            }
            Set<String> tosKeys = lastItem.getTos().keySet();
            for (String toKey : tosKeys) {
                Long toId = Long.parseLong(toKey);
                if (toId == null || toId == 0) {
                    log.error("can't parse toId for toKey : " + toKey + " in issue : " + lastItem.getIssueKey());
                    continue;
                }
                if (deletedAttachmentsIds.contains(toId)) {
                    continue;
                }
                Attachment attachment =  attachmentManager.getAttachment(toId);
                if (attachment == null) {
                    log.error("can't fetch attachment with id : " + toId + " for issue : " + lastItem.getIssueKey());
                    continue;
                }
                if (!user.getName().equals(attachment.getAuthor())) {
                    log.debug("attachment author and comment author is not equals for issue : " + lastItem.getIssueKey());
                    continue;
                }
                result.add(attachment);
            }
        }
        log.debug("end of getLastAttachments(); result size : " + result.size());
        return result;
    }

    private boolean isRelative(String attachmentBase) {
        return RELATIVE_COMPILED_PATTERN.matcher(attachmentBase).matches();
    }


    @Deprecated
    private Email createEmail(IssueEvent issueEvent, List<Map<String, Object>> attachmentsProps, boolean isHtml, boolean isComment) throws IOException, MessagingException, VelocityException {
        String subjectTemplate;
        String bodyTemplate;
        if (isComment) {
            subjectTemplate = ISSUE_COMMENTED_SUBJECT_TEMPLATE;
        } else {
            subjectTemplate = ISSUE_CREATED_SUBJECT_TEMPLATE;
        }
        //log.debug("creating email for issue : " + issueEvent.getIssue().getKey());
        Email email = new Email(issueEvent.getIssue().getReporter().getEmailAddress());
        Map<String, Object> context = createContext(issueEvent,isHtml,isComment);
        // actually deprecated one is this :

        String fromEmail = computateFromEmail(issueEvent, isComment);

        email.setFrom(fromEmail);
        if (isComment)
            email.setFromName(issueEvent.getUser().getDisplayName());
        else email.setFromName(MEGAPLAN_FROM_NAME);
        email.setSubject(render(subjectTemplate, context));
        String plainText = isComment?render(ISSUE_COMMENTED_BODY_TEMPLATE, context):render(ISSUE_CREATED_BODY_TEMPLATE, context);
        String htmlText = null;
        if (isHtml) htmlText = isComment?render(ISSUE_COMMENTED_BODY_HTML_TEMPLATE, context):render(ISSUE_CREATED_BODY_HTML_TEMPLATE, context);
        boolean hasAttach = attachmentsProps != null && !attachmentsProps.isEmpty();
        if (!hasAttach && !isHtml) email.setBody(plainText);
        else {
            email.setMultipart(getMultipart(issueEvent, plainText, htmlText, attachmentsProps, hasAttach, isHtml));
        }
        return email;
    }

    private String computateFromEmail(IssueEvent issueEvent, boolean isComment) {
        String fromEmail;
        String projectEmail =
                OFBizPropertyUtils.getPropertySet(
                        issueEvent.getProject().getGenericValue()
                ).getString(ProjectKeys.EMAIL_SENDER);
        // https://answers.atlassian.com/questions/45425/how-to-get-project-email-via-new-jira-api
        // https://jira.atlassian.com/browse/JRA-27754
        if (projectEmail == null) {
            log.error("project email is null, I would use user email or default email");
            fromEmail = DEFAULT_FROM_EMAIL;
        } else {
            fromEmail = projectEmail;
        }
        if (serviceCompanyCF != null) {
            Object serviceCompanyCfValue = serviceCompanyCF.getValue(issueEvent.getIssue());
            if (serviceCompanyCfValue != null && serviceCompanyCfValue instanceof Option) {
                Option serviceCompanyOption = (Option) serviceCompanyCfValue;
                String optionValue = serviceCompanyOption.getValue();
                String fromEmailServiceCompany = RegionAssigneesMapper.INSTANCE.getEmailForServiceCompany(optionValue);
                if (fromEmailServiceCompany != null) {
                    fromEmail = fromEmailServiceCompany;
                }
            }
        }
        return fromEmail;
    }

    private Multipart getMultipart(IssueEvent issueEvent, String plainText, String htmlText, List<Map<String, Object>> attachmentsProps, boolean hasAttach, boolean isHtml) throws MessagingException, IOException {
        Multipart multipart = new MimeMultipart("related");

        //log.warn("new mp content type : " + multipart.getContentType());
        MimeBodyPart textPart = new MimeBodyPart();
        if (isHtml) {
            Multipart textPartContent = new MimeMultipart("alternative");
            MimeBodyPart plainPart = new MimeBodyPart();
            plainPart.setText(plainText, "UTF-8");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setText(htmlText, "UTF-8", "html");
            textPartContent.addBodyPart(plainPart);
            textPartContent.addBodyPart(htmlPart);
            textPart.setContent(textPartContent);
        } else {
            textPart.setText(plainText, "UTF-8");
        }
        multipart.addBodyPart(textPart);
        if (hasAttach) {
            for (Map<String, Object> attachmentProp : attachmentsProps) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
       //         log.warn("Setting filename : " + attachmentProp.get(A_FILENAME));
                attachmentPart.setFileName(MimeUtility.encodeText(attachmentProp.get(A_FILENAME).toString()));
       //         log.warn("setting file : " + attachmentProp.get(A_PATH));
                DataSource source =
                        new FileDataSource(attachmentProp.get(A_PATH).toString());
       //         log.warn("found content type : " + source.getContentType());
                attachmentPart.setDataHandler(
                        new DataHandler(source));
                multipart.addBodyPart(attachmentPart);
            }
        }
        return multipart;
    }

    private Multipart getMultipart(IssueEvent issueEvent,
                                   String text, List<Map<String, Object>> attachmentsProps,
                                   String htmlText) throws IOException, MessagingException {
        int bodyPartNum = 0;
        Multipart multipart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        log.debug("Setting text part : " + text);
        textPart.setText(text, "UTF-8");
        multipart.addBodyPart(textPart,bodyPartNum);
        for (Map<String, Object> attachmentProp : attachmentsProps) {
            ++bodyPartNum;
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.setFileName(attachmentProp.get(A_FILENAME).toString());
            DataSource source =
                    new FileDataSource(attachmentProp.get(A_PATH).toString());
            attachmentPart.setDataHandler(
                    new DataHandler(source));
            multipart.addBodyPart(attachmentPart, bodyPartNum);
        }

        return multipart;
    }

    private String render(String template, Map<String, Object> context) throws VelocityException {
        String baseUrl = (String) applicationProperties.asMap().get(APKeys.JIRA_BASEURL);
        return velocityManager.getEncodedBody("/",template,baseUrl,"UTF-8",context);
    }

    private Map<String, Object> createContext(IssueEvent issueEvent, boolean isHtml, boolean isComment) {
        Map<String, Object> result = velocityParamFactory.getDefaultVelocityParams(jiraAuthenticationContext);
        MutableIssue issue = issueManager.getIssueObject(issueEvent.getIssue().getId()); //it is for summary
        result.put("i18n", jiraAuthenticationContext.getI18nHelper());
        result.put("issue", issue);
        if (isComment) {
            if (isHtml) {
                String commentBody = issueEvent.getComment().getBody();
                String commentText = StringEscapeUtils.escapeHtml(commentBody).replace("\n","<br/>");
                result.put("commentText", commentText);
            } else {
                result.put("commentText", issueEvent.getComment().getBody());
            }
            result.put("commentAuthorFullName", issueEvent.getComment().getAuthorFullName());
            User commentAuthor = issueEvent.getComment().getAuthorUser();
            if (commentAuthor != null) {
                result.put("commentAuthorGradeName", getMpsGrade(commentAuthor));
            }

        }
        result.put("changelog", issueEvent.getChangeLog());
        result.put("jirakeyutils", jiraKeyUtils);
        try {
            CustomField loginCustomField = customFieldManager.getCustomFieldObject(LOGINCUSTOMFIELDID);
            if (loginCustomField == null) {
                log.error("Account name custom field with id : " + LOGINCUSTOMFIELDID + " not found");
            }
            String megaplanLogin = (String) issueEvent.getIssue().getCustomFieldValue(loginCustomField);
            result.put("megaplanLogin", megaplanLogin);
        } catch (Exception e) {
            log.error("some error in getting megaplanLogin custom field");
            log.error(e);
        }
        try {
            log.debug("reporter : " + issueEvent.getIssue().getReporter().getName());
            String reporterFirstName = userPropertyManager.getPropertySet(issueEvent.getIssue().getReporter()).getString("jira.meta."+USER_FIRST_NAME_PROPERTY);
            result.put("reporterFirstName", reporterFirstName);

            if (log.isDebugEnabled()) {
                StringBuilder stringBuilder = new StringBuilder("attachmentus context map : \n");
                for (Map.Entry<String, Object> e : result.entrySet()) {
                    stringBuilder.append(e.getKey() + " " + e.getValue());
                }
                log.debug(stringBuilder.toString());
            }
        } catch (Exception e) {
            log.error("some error in getting reporterFirstName parameter");
            log.error("error",e);
        }
        try {
            CustomField originalSummaryCustomField = customFieldManager.getCustomFieldObject(ORIGINAL_SUMMARY_FIELD_ID);
            if (originalSummaryCustomField == null) {
                log.error("originalSummaryCustomField with id : " + ORIGINAL_SUMMARY_FIELD_ID + " not found");
            }
            String originalSummary = (String) issueEvent.getIssue().getCustomFieldValue(originalSummaryCustomField);
            log.debug("originalSummary in attachmentus : " + originalSummary);
            if (originalSummary != null) issue.setSummary(originalSummary);
            log.debug("issue.getSummary() : " + issue.getSummary());
        } catch (Exception e) {
            log.error("some error in getting OriginalSummary field");
            log.error("error",e);
        }

        return result;
    }

    @Nullable
    private String getMpsGrade(User commentAuthor) {
        return userPreferencesManager.getPreferences(commentAuthor).getString(GRADE_PROPERTY_KEY_PREFIXED);
    }

    private boolean sanitizeReporterIsCommentator(Issue issue, Comment comment) {
        if (issue.getReporter().equals(comment.getAuthorUser())) return true;
        else return false;
    }
    private boolean sanitizeCommentPermissions(Comment comment) {
        if (comment.getGroupLevel() != null || comment.getRoleLevel() != null) return true;
        else return false;
    }


    private boolean likeEmail(String name) {
        Matcher matcher = EMAIL_COMPILED_PATTERN.matcher(name);
        return matcher.matches();
    }

    public boolean isWorkTime() {
        Date currentTime = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(currentTime);
        int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
        return (hourOfDay > 10 && hourOfDay <= 19);
    }

    @Override
    public void destroy() throws Exception {
        eventPublisher.unregister(this);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
    }
}
