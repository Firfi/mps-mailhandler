package ru.megaplan.jira.plugins.mail.mpsmailhandler;

import com.atlassian.core.util.RandomGenerator;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.ComponentManager;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.PriorityManager;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.event.user.UserEventType;
import com.atlassian.jira.exception.CreateException;
import com.atlassian.jira.issue.*;
import com.atlassian.jira.issue.customfields.impl.SelectCFType;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.SummaryField;
import com.atlassian.jira.issue.history.ChangeItemBean;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.security.IssueSecurityLevelManager;
import com.atlassian.jira.mail.MailThreadManager;
import com.atlassian.jira.plugin.assignee.AssigneeResolver;
import com.atlassian.jira.plugins.mail.handlers.CreateIssueHandler;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.service.util.handler.MessageHandlerContext;
import com.atlassian.jira.service.util.handler.MessageHandlerErrorCollector;
import com.atlassian.jira.user.UserUtils;
import com.atlassian.jira.web.FieldVisibilityManager;
import com.atlassian.jira.workflow.WorkflowFunctionUtils;
import com.atlassian.mail.MailUtils;
import com.atlassian.util.concurrent.Nullable;
import com.opensymphony.util.TextUtils;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericValue;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.AdditionalAccountInfoService;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.impl.AdditionalAccountInfoServiceImpl;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util.CustomFieldMapperUtil;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util.RegionAssigneesMapper;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.MPMessage;

import javax.mail.*;
import javax.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 02.07.12
 * Time: 11:50
 * To change this template use File | Settings | File Templates.
 */
public class CreateMPIssueHandler extends CreateIssueHandler {

    private XStream xStream;
    private static Pattern xmlAttachFilenamePattern = Pattern.compile("^account_.*_[0-9]+\\.xml$");

    private final static Logger log = Logger.getLogger(CreateMPIssueHandler.class);

    private CustomFieldMapperUtil.FieldMapper fieldMapper;
    private AdditionalAccountInfoService additionalAccountInfoService;



    @Override
    public void init(Map<String,String> params, MessageHandlerErrorCollector errorCollector) {
        super.init(params, errorCollector);
        fieldMapper = new CustomFieldMapperUtil.FieldMapper();
        xStream = new XStream(new DomDriver());
        xStream.processAnnotations(MPMessage.class);
    }

    public static int accountXmlIsAttached(Message message) throws IOException, MessagingException {
        log.debug("start checking xml is attached");
        if (message.getContent() instanceof Multipart) {
            log.debug("message content is multipart");
            Multipart content = (Multipart) message.getContent();
            for (int i = 0; i < content.getCount(); ++i) {
                BodyPart bodyPart = content.getBodyPart(i);
                log.debug("BodyPartFile : " + bodyPart.getFileName());
                log.debug(bodyPart.getContent());
                if (bodyPart.getFileName() !=null && !bodyPart.getFileName().isEmpty())
                {
                    Matcher m = xmlAttachFilenamePattern.matcher(bodyPart.getFileName());
                    if (m.matches()) {
                        log.debug("pattern matches for filename : " + bodyPart.getFileName());
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    @Override
    public boolean handleMessage(Message message, MessageHandlerContext context) throws MessagingException
    {
        try
        {


            if (!canHandleMessage(message, context.getMonitor()))
            {
                return deleteEmail;
            }

            int bodyPartNum = -1;

            try {
                bodyPartNum = accountXmlIsAttached(message);
            } catch (IOException e) {
                log.error("io exception in reading body part",e);
                throw e;
            }

            if (bodyPartNum == -1) {
                log.error("can't found xml attachment");
                return false;
            }

            MPMessage mpMessage = getMpMessage(message,bodyPartNum);

            if (mpMessage == null) {
                log.error("some error in obtaining MPMessage from xml string content");
                return false;
            }

            // get either the sender of the message, or the default reporter
            User reporter = getReporter(mpMessage, message, context);

            // no reporter - so reject the message
            if (reporter == null)
            {
                final String error = "can't find reporter in xml for message : " + message.getSubject();
                context.getMonitor().warning(error);
                context.getMonitor().messageRejected(message, error);
                return false;
            }

            final Project project = getProject(message);

            log.debug("Project = " + project);
            if (project == null)
            {
                final String text = getI18nBean().getText("admin.mail.no.project.configured");
                context.getMonitor().warning(text);
                context.getMonitor().messageRejected(message, text);
                return false;
            }


            // If user does not have create permissions, there's no point proceeding. Error out here to avoid a stack
            // trace blow up from the WorkflowManager later on.
            if (!getPermissionManager().hasPermission(Permissions.CREATE_ISSUE, project, reporter, true) && reporter.getDirectoryId() != -1)
            {
                final String error = getI18nBean().getText("admin.mail.no.create.permission", reporter.getName());
                context.getMonitor().warning(error);
                context.getMonitor().messageRejected(message, error);
                return false;
            }

            log.debug("Issue Type Key = = " + issueType);

            if (!hasValidIssueType())
            {
                context.getMonitor().warning(getI18nBean().getText("admin.mail.invalid.issue.type"));
                return false;
            }
            String summary = mpMessage.getAccount().getSubject();
            if (!TextUtils.stringSet(summary))
            {
                context.getMonitor().error(getI18nBean().getText("admin.mail.no.subject"));
                return false;
            }
            if (summary.length() > SummaryField.MAX_LEN.intValue())
            {
                context.getMonitor().info("Truncating summary field because it is too long: " + summary);
                summary = summary.substring(0, SummaryField.MAX_LEN.intValue() - 3) + "...";
            }

            // JRA-7646 - check if priority/description is hidden - if so, do not set
            String description = null;


            if (!getFieldVisibilityManager().isFieldHiddenInAllSchemes(project.getId(),
                    IssueFieldConstants.DESCRIPTION, Collections.singletonList(issueType)))
            {
                description = mpMessage.getAccount().getQuestion();
            }

            MutableIssue issueObject = getIssueFactory().getIssue();
            issueObject.setProjectObject(project);
            issueObject.setSummary(summary);
            issueObject.setDescription(description);
            issueObject.setIssueTypeId(issueType);
            issueObject.setReporter(reporter);

            // we really don't need it when there is priority setting in setCustomFields
            PriorityManager priorityManager = ComponentAccessor.getComponentOfType(PriorityManager.class);
            if (priorityManager != null) {
                Priority defaultPriority = priorityManager.getDefaultPriority();
                if (defaultPriority != null) {
                    issueObject.setPriorityObject(defaultPriority);
                }
            }



            // if no valid assignee found, attempt to assign to default assignee
            User assignee = null;
            if (ccAssignee)
            {
                assignee = getFirstValidAssignee(message.getAllRecipients(), project);
            }
            if (assignee == null)
            {
                assignee = getAssigneeResolver().getDefaultAssigneeObject(issueObject, Collections.EMPTY_MAP);
            }

            if (assignee != null)
            {
                if (assignee.equals(issueObject.getReporter())) {
                    log.error("assignee cant'be reporter here!");
                    assignee = userManager.getUser("megaplan");
                }
                if (assignee != null) {
                    log.debug("assignee : " + assignee.getName());
                    issueObject.setAssignee(assignee);
                }
            }

            // Ensure issue level security is correct
            setDefaultSecurityLevel(issueObject);

            /*
             * + FIXME -- set cf defaults @todo +
             */
            // set default custom field values
            // CustomFieldValuesHolder cfvh = new CustomFieldValuesHolder(issueType, project.getId());
            // fields.put("customFields", CustomFieldUtils.getCustomFieldValues(cfvh.getCustomFields()));
            Map<String, Object> fields = new HashMap<String, Object>();
            fields.put("issue", issueObject);
            // TODO: How is this supposed to work? There is no issue created yet; ID = null.
            // wseliga note: Ineed I think that such call does not make sense - it will be always null
            MutableIssue originalIssue = getIssueManager().getIssueObject(issueObject.getId());

            // Give the CustomFields a chance to set their default values JRA-11762
            List<CustomField> customFieldObjects = ComponentAccessor.getCustomFieldManager().getCustomFieldObjects(issueObject);
            for (CustomField customField : customFieldObjects)
            {
                issueObject.setCustomFieldValue(customField, customField.getDefaultValue(issueObject));
            }



            setCustomFields(issueObject,mpMessage);

            RegionAssigneesMapper.INSTANCE.setRegionAssignee(issueObject, message);

            fields.put(WorkflowFunctionUtils.ORIGINAL_ISSUE_KEY, originalIssue);
            final Issue issue = context.createIssue(reporter, issueObject);

            if (issue != null)
            {
                // Add Cc'ed users as watchers if params set - JRA-9983
                if (ccWatcher)
                {
                    addCcWatchersToIssue(message, issue, reporter, context, context.getMonitor());
                }

                // Record the message id of this e-mail message so we can track replies to this message
                // and associate them with this issue
                recordMessageId(MailThreadManager.ISSUE_CREATED_FROM_EMAIL, message, issue.getId(), context);
            }

            // TODO: if this throws an error, then the issue is already created, but the email not deleted - we will keep "handling" this email over and over :(

            createAttachmentsForMessage(message, issue, context, mpMessage);

            return true;
        }
        catch (Exception e)
        {
            log.error("exception in mail handling:", e);
            context.getMonitor().warning(getI18nBean().getText("admin.mail.unable.to.create.issue"), e);
        }

        // something went wrong - don't delete the message
        return false;
    }

    private MutableIssue setCustomFields(MutableIssue issueObject, MPMessage mpMessage) {
        log.debug("setting custom fields for new issue");
        String accountId = mpMessage.getAccount().getId();
        Map<CustomField, Object> additional = null;
        if (accountId != null && !accountId.isEmpty()) {
            try {
                Long id = Long.valueOf(accountId);
                if (additionalAccountInfoService == null) additionalAccountInfoService = new AdditionalAccountInfoServiceImpl();
                additional = additionalAccountInfoService.getAdditionalAccountInfo(id);
            } catch (NumberFormatException e) {
                log.error("some trash in account id field : " + accountId, e);
            } catch (IllegalStateException e) {
                log.error("can't obtain additional info");
            }
        }
        return fieldMapper.setCustomFields(issueObject, mpMessage, additional);
    }

    private MPMessage getMpMessage(Message message, int bodyPartNum) throws IOException, MessagingException {
        Multipart content = (Multipart) message.getContent();
        BodyPart bp = content.getBodyPart(bodyPartNum);
        InputStreamReader inputStreamReader = new InputStreamReader(bp.getInputStream(),"UTF-8");
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        return (MPMessage) xStream.fromXML(bufferedReader);
    }

    PermissionManager getPermissionManager()
    {
        return ComponentAccessor.getPermissionManager();
    }
    IssueFactory getIssueFactory()
    {
        return ComponentAccessor.getIssueFactory();
    }
    FieldVisibilityManager getFieldVisibilityManager()
    {
        return ComponentAccessor.getComponent(FieldVisibilityManager.class);
    }
    private String getDescription(User reporter, Message message) throws MessagingException
    {
        return recordFromAddressForAnon(reporter, message, MailUtils.getBody(message));
    }
    IssueSecurityLevelManager getIssueSecurityLevelManager()
    {
        return ComponentAccessor.getIssueSecurityLevelManager();
    }
    IssueManager getIssueManager()
    {
        return ComponentAccessor.getIssueManager();
    }
    AssigneeResolver getAssigneeResolver()
    {
        return ComponentAccessor.getComponent(AssigneeResolver.class);
    }
    private String recordFromAddressForAnon(User reporter, Message message, String description) throws MessagingException
    {
        // If the message has been created for an anonymous user add the senders e-mail address to the description.
        if (reporteruserName != null && reporteruserName.equals(reporter.getName()))
        {
            description += "\n[Created via e-mail ";
            if (message.getFrom() != null && message.getFrom().length > 0)
            {
                description += "received from: " + message.getFrom()[0] + "]";
            }
            else
            {
                description += "but could not establish sender's address.]";
            }
        }
        return description;
    }
    private void setDefaultSecurityLevel(MutableIssue issue) throws Exception
    {
        GenericValue project = issue.getProject();
        if (project != null)
        {
            final Long levelId = getIssueSecurityLevelManager().getSchemeDefaultSecurityLevel(project);
            if (levelId != null)
            {
                issue.setSecurityLevel(getIssueSecurityLevelManager().getIssueSecurity(levelId));
            }
        }
    }

    @Override
    protected User getReporter(final Message message, MessageHandlerContext context) throws MessagingException {
        throw new RuntimeException("call getReporter(MPMessage) please");
    }


    protected User getReporter(final MPMessage message, Message mail, MessageHandlerContext context) throws MessagingException
    {
        log.debug("getting reporter from MPMessage");
        User reporter = userManager.getUser(message.getAccount().getPerson().getEmail());


        if (reporter == null)
        {
            //if createUsers is set, attempt to create a new reporter from the e-mail details
            if (createUsers)
            {
                reporter = createUserForReporter(message, mail, context);
            }

            // If there's a default reporter set, and we haven't created a reporter yet, attempt to use the
            //default reporter.
            if ((reporteruserName != null) && (reporter == null))
            {
                // Sender not registered with JIRA, use default reporter
                reporter = UserUtils.getUser(reporteruserName);
            }
        }
        return reporter;
    }

    @Nullable
    protected User createUserForReporter(final MPMessage message, Message mail, MessageHandlerContext context)
    {
        log.warn("creating user for reporter : " + message.getAccount().getPerson().getEmail());
        User reporter = null;
        String reporterEmail = null;
        try
        {
            if (!userManager.hasWritableDirectory())
            {
                context.getMonitor().warning("Unable to create user for reporter because no user directories are writable.");
                return null;
            }

            // If reporter is not a recognised user, then create one from the information in the e-mail
            log.debug("Cannot find reporter for message. Creating new user.");


            reporterEmail = message.getAccount().getPerson().getEmail();
            log.warn("reporter email : " + reporterEmail);
            if (!TextUtils.verifyEmail(reporterEmail))
            {
                context.getMonitor().error("The email address [" + reporterEmail + "] received was not valid. Ensure that your mail client specified a valid 'From:' mail header. (see JRA-12203)");
                return null;
            }
            String fullName =
                    (message.getAccount().getPerson().getFirstname()==null?"":message.getAccount().getPerson().getFirstname())
                    + " "
                    + (message.getAccount().getPerson().getLastname()==null?"":message.getAccount().getPerson().getLastname());
            if ((fullName == null) || (fullName.trim().length() == 0))
            {
                fullName = reporterEmail;
            }

            final String password = RandomGenerator.randomPassword();
            if (notifyUsers)
            {
                reporter = context.createUser(reporterEmail, password, reporterEmail, fullName, UserEventType.USER_CREATED);
            }
            else
            {
                reporter = context.createUser(reporterEmail, password, reporterEmail, fullName, null);
            }
            if (reporter == null) { // maybe scary shit happened here
                User errorReporter = userManager.getUser(reporterEmail);
                if (errorReporter != null) {
                    String error = "Error in creating user. Maybe we should reboot jira instance.";
                    log.error(error);
                    MPSMessageHandler.removeUser(errorReporter);
                }
                throw new NullPointerException("created user was null");
            }
            if (context.isRealRun())
            {
                log.warn("Created user " + reporterEmail + " as reporter of email-based issue.");
                MPSMessageHandler.addEmailGroup(reporter);
                log.warn(1);
                MPSMessageHandler.addProperties(reporter);
                log.warn(2);
            }
        }
        catch (final Exception e)
        {
            context.getMonitor().error("Error occurred while automatically creating a new user from email", e);
            log.error("exception in creating user attributes for user : " + message.getAccount().getPerson().getEmail());
            MPSMessageHandler.removeUser(userManager.getUser(reporterEmail));

        }
        MPSMessageHandler.checkValidity(reporter, mail, context);
        return reporter;
    }

    protected Collection<ChangeItemBean> createAttachmentsForMessage(final Message message, final Issue issue,
                                                                     MessageHandlerContext context, MPMessage mpMessage) throws IOException, MessagingException
    {
        final Collection<ChangeItemBean> attachmentChangeItems = new ArrayList<ChangeItemBean>();
        if (applicationProperties.getOption(APKeys.JIRA_OPTION_ALLOWATTACHMENTS))
        {
            final String disposition = message.getDisposition();
            if (message.getContent() instanceof Multipart)
            {
                final Multipart multipart = (Multipart) message.getContent();
                final Collection<ChangeItemBean> changeItemBeans = handleMultipart(multipart, message, issue,
                        context, mpMessage);
                if ((changeItemBeans != null) && !changeItemBeans.isEmpty())
                {
                    attachmentChangeItems.addAll(changeItemBeans);
                }
            }
            //JRA-12123: Message is not a multipart, but it has a disposition of attachment.  This means that
            //we got a message with an empty body and an attachment.  We'll ignore inline.
            else if (Part.ATTACHMENT.equalsIgnoreCase(disposition))
            {
                if (log.isDebugEnabled())
                {
                    log.debug("Trying to add attachment to issue from attachment only message.");
                }

                final ChangeItemBean res = saveAttachmentIfNecessary(message, null, getReporter(mpMessage, message, context), issue, context);
                if (res != null)
                {
                    attachmentChangeItems.add(res);
                }
            }
        }
        else
        {
            if (log.isDebugEnabled())
            {
                log.debug("Unable to add message attachements to issue: JIRA Attachements are disabled.");
            }
        }

        return attachmentChangeItems;
    }

    private Collection<ChangeItemBean> handleMultipart(final Multipart multipart, final Message message, final Issue issue,
                                                       MessageHandlerContext context, MPMessage mpMessage) throws MessagingException, IOException
    {
        final Collection<ChangeItemBean> attachmentChangeItems = new ArrayList<ChangeItemBean>();
        for (int i = 0, n = multipart.getCount(); i < n; i++)
        {
            if (log.isDebugEnabled())
            {
                log.debug(String.format("Adding attachments for multi-part message. Part %d of %d.", i + 1, n));
            }

            final BodyPart part = multipart.getBodyPart(i);

            if (part.getFileName() != null) {
                Matcher m = xmlAttachFilenamePattern.matcher(part.getFileName());
                if (m.matches()) continue;
            }

            // there may be non-attachment parts (e.g. HTML email) - fixes JRA-1842
            final boolean isContentMultipart = part.getContent() instanceof Multipart;
            if (isContentMultipart)
            {
                // Found another multi-part - process it and collection all change items.
                attachmentChangeItems.addAll(handleMultipart((Multipart) part.getContent(), message, issue, context, mpMessage));
            }
            else
            {
                // JRA-15133: if this part is an attached message, skip it if:
                // * the option to ignore attached messages is set to true, OR
                // * this message is in reply to the attached one (redundant info)
                // Note: this is now covered by the shouldAttach() method
                final ChangeItemBean res = saveAttachmentIfNecessary(part, message, getReporter(mpMessage, message, context), issue, context);
                if (res != null)
                {
                    attachmentChangeItems.add(res);
                }
            }
        }

        return attachmentChangeItems;
    }


    private ChangeItemBean saveAttachmentIfNecessary(final Part part, final Message containingMessage, final User reporter,
                                                     final Issue issue, MessageHandlerContext context) throws MessagingException, IOException
    {
        final boolean keep = shouldAttach(part, containingMessage);
        if (keep)
        {
            return createAttachmentWithPart(part, reporter, issue, context);
        }
        return null;
    }



}
