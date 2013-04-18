package ru.megaplan.jira.plugins.mail.mpsmailhandler;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.*;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.SummarySystemField;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.security.IssueSecurityLevelManager;
import com.atlassian.jira.mail.MailThreadManager;
import com.atlassian.jira.plugin.assignee.AssigneeResolver;
import com.atlassian.jira.plugins.mail.handlers.CreateIssueHandler;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.Permissions;
import com.atlassian.jira.service.util.handler.MessageHandlerContext;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.I18nHelper;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.web.FieldVisibilityManager;
import com.atlassian.jira.web.action.issue.IssueCreationHelperBean;
import com.atlassian.jira.web.bean.I18nBean;
import com.atlassian.jira.workflow.WorkflowFunctionUtils;
import com.atlassian.mail.MailUtils;
import com.opensymphony.util.TextUtils;
import org.apache.log4j.Logger;
import org.ofbiz.core.entity.GenericValue;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util.RegionAssigneesMapper;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.util.MessageProxyFrom;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.atlassian.jira.ComponentManager.getComponentInstanceOfType;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 30.07.12
 * Time: 19:19
 * To change this template use File | Settings | File Templates.
 */
public class CreateIssueHandlerExtended extends CreateIssueHandler {

    private final static Logger log = Logger.getLogger(CreateIssueHandlerExtended.class);



    @Override
    public boolean handleMessage(Message message, MessageHandlerContext context) throws MessagingException
    {
        log.debug("CreateIssueHandlerExtended.handleMessage");

        if (!canHandleMessage(message, context.getMonitor()))
        {
            return deleteEmail;
        }

        try
        {
            // get either the sender of the message, or the default reporter

            User reporter = null;

            if (MPSMessageHandler.sanitizeAuthor(message.getFrom(), MPSMessageHandler.MEGADOMAIN) && message.getSubject().startsWith("Fwd: ")) {
                log.warn("sanitized message with author " + Arrays.toString(message.getFrom()));
                Pattern reporterFromSubject = Pattern.compile(
                        ".*<<([_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,}))>>"  // <<email@email.com>>
                );
                Matcher m = reporterFromSubject.matcher(message.getSubject());
                if (m.find()) {
                    final String email = m.group(1);
                    log.warn("gettin' reporter for email: " + email);
                    reporter = getReporter(new MessageProxyFrom(message, new InternetAddress(email)), context);
                    log.warn("new reporter : " + reporter);
                }
            }

            if (reporter == null) reporter = getReporter(message, context);

            // no reporter - so reject the message
            if (reporter == null)
            {
                final String error = getI18nBean().getText("admin.mail.no.default.reporter");
                context.getMonitor().warning(error);
                context.getMonitor().messageRejected(message, error);
                return true;
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

            // Check that the license is valid before allowing issues to be created
            // This checks for: evaluation licenses expired, user limit licenses where limit has been exceeded
            ErrorCollection errorCollection = new SimpleErrorCollection();
            // Note: want English locale here for logging purposes
            I18nHelper i18nHelper = new I18nBean(Locale.ENGLISH);

            getIssueCreationHelperBean().validateLicense(errorCollection, i18nHelper);
            if (errorCollection.hasAnyErrors())
            {
                context.getMonitor().warning(getI18nBean().getText("admin.mail.bad.license", errorCollection.getErrorMessages().toString()));
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
            String summary = message.getSubject();
            if (!TextUtils.stringSet(summary))
            {
                context.getMonitor().error(getI18nBean().getText("admin.mail.no.subject"));
                return false;
            }
            if (summary.length() > SummarySystemField.MAX_LEN.intValue())
            {
                context.getMonitor().info("Truncating summary field because it is too long: " + summary);
                summary = summary.substring(0, SummarySystemField.MAX_LEN.intValue() - 3) + "...";
            }

            // JRA-7646 - check if priority/description is hidden - if so, do not set
            String priority = null;
            String description = null;

            if (!getFieldVisibilityManager().isFieldHiddenInAllSchemes(project.getId(),
                    IssueFieldConstants.PRIORITY, Collections.singletonList(issueType)))
            {
                priority = getPriority(message);
            }

            if (!getFieldVisibilityManager().isFieldHiddenInAllSchemes(project.getId(),
                    IssueFieldConstants.DESCRIPTION, Collections.singletonList(issueType)))
            {
                description = getDescription(reporter, message);
            }

            MutableIssue issueObject = getIssueFactory().getIssue();
            issueObject.setProjectObject(project);
            issueObject.setSummary(summary);
            issueObject.setDescription(description);
            issueObject.setIssueTypeId(issueType);
            issueObject.setReporter(reporter);

            // if no valid assignee found, attempt to assign to default assignee
            User assignee = null;
            if (ccAssignee)
            {
                assignee = getFirstValidAssignee(message.getAllRecipients(), project);
            }
            if (assignee == null)
            {
                assignee = getAssigneeResolver().getDefaultAssignee(issueObject, Collections.EMPTY_MAP);
            }

            if (assignee != null)
            {
                issueObject.setAssignee(assignee);
            }

            issueObject.setPriorityId(priority);

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

            fields.put(WorkflowFunctionUtils.ORIGINAL_ISSUE_KEY, originalIssue);
            RegionAssigneesMapper.INSTANCE.setRegionAssignee(issueObject, message);
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
            createAttachmentsForMessage(message, issue, context);

            log.warn("created issue and attachments: " + issue.getSummary() + " " + issue.getKey());

            return true;
        }
        catch (Exception e)
        {
            context.getMonitor().warning(getI18nBean().getText("admin.mail.unable.to.create.issue"), e);
        }

        // something went wrong - don't delete the message
        return false;
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

    IssueSecurityLevelManager getIssueSecurityLevelManager()
    {
        return getComponentInstanceOfType(IssueSecurityLevelManager.class);
    }

    private IssueCreationHelperBean getIssueCreationHelperBean()
    {
        return ComponentAccessor.getComponent(IssueCreationHelperBean.class);
    }

    PermissionManager getPermissionManager()
    {
        return ComponentAccessor.getPermissionManager();
    }

    IssueManager getIssueManager()
    {
        return ComponentAccessor.getIssueManager();
    }

    AssigneeResolver getAssigneeResolver()
    {
        return ComponentAccessor.getComponent(AssigneeResolver.class);
    }

    FieldVisibilityManager getFieldVisibilityManager()
    {
        return ComponentAccessor.getComponent(FieldVisibilityManager.class);
    }

    IssueFactory getIssueFactory()
    {
        return ComponentAccessor.getIssueFactory();
    }

    private String getDescription(User reporter, Message message) throws MessagingException
    {
        return recordFromAddressForAnon(reporter, message, MailUtils.getBody(message));
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

    private String getPriority(Message message) throws MessagingException
    {
        String[] xPrioHeaders = message.getHeader("X-Priority");

        if (xPrioHeaders != null && xPrioHeaders.length > 0)
        {
            String xPrioHeader = xPrioHeaders[0];

            int priorityValue = Integer.parseInt(TextUtils.extractNumber(xPrioHeader));

            if (priorityValue == 0)
            {
                return getDefaultSystemPriority();
            }

            // if priority is unset - pick the closest priority, this should be a sensible default
            Collection<Priority> priorities = getConstantsManager().getPriorityObjects();

            Iterator<Priority> priorityIt = priorities.iterator();

            /*
             * NOTE: Valid values for X-priority are (1=Highest, 2=High, 3=Normal, 4=Low & 5=Lowest) The X-Priority
             * (priority in email header) is divided by 5 (number of valid values) this gives the percentage
             * representation of the priority. We multiply this by the priority.size() (number of priorities in jira) to
             * scale and map the percentage to a priority in jira.
             */
            int priorityNumber = (int) Math.ceil(((double) priorityValue / 5d) * (double) priorities.size());
            // if priority is too large, assume its the 'lowest'
            if (priorityNumber > priorities.size())
            {
                priorityNumber = priorities.size();
            }

            String priority = null;

            for (int i = 0; i < priorityNumber; i++)
            {
                priority = priorityIt.next().getId();
            }

            return priority;
        }
        else
        {
            return getDefaultSystemPriority();
        }
    }

    ConstantsManager getConstantsManager()
    {
        return getComponentInstanceOfType(ConstantsManager.class);
    }

    private String getDefaultSystemPriority()
    {
        // if priority header is not set, assume it's 'default'
        Priority defaultPriority = getConstantsManager().getDefaultPriorityObject();
        if (defaultPriority == null)
        {
            Collection<Priority> priorities = getConstantsManager().getPriorityObjects();
            final int times = (int) Math.ceil((double) priorities.size() / 2d);
            Iterator<Priority> priorityIt = priorities.iterator();
            for (int i = 0; i < times; i++)
            {
                defaultPriority = priorityIt.next();
            }
        }
        if (defaultPriority == null)
        {
            throw new RuntimeException("Default priority not found");
        }
        return defaultPriority.getId();
    }

}
