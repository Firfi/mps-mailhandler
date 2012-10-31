package ru.megaplan.jira.plugins.mail.attachmentus.customfield;

import com.atlassian.jira.datetime.DateTimeFormatter;
import com.atlassian.jira.datetime.DateTimeFormatterFactory;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.customfields.impl.CalculatedCFType;
import com.atlassian.jira.issue.customfields.impl.FieldValidationException;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.sal.api.message.I18nResolver;
import org.apache.log4j.Logger;
import ru.megaplan.jira.plugins.mail.attachmentus.AttachmentusListener;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static com.atlassian.jira.datetime.DateTimeStyle.COMPLETE;
import static com.google.common.base.Preconditions.checkNotNull;


/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 6/30/12
 * Time: 1:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class AttachmentusCFType extends CalculatedCFType<String, String> {

    private final static Logger log = Logger.getLogger(AttachmentusCFType.class);

    private final AttachmentusListener attachmentusListener;
    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final DateTimeFormatterFactory dateTimeFormatterFactory;
    private final I18nResolver i18nResolver;
    private final String A_PATH = AttachmentusListener.A_PATH;
    private final String A_MIME_TYPE = AttachmentusListener.A_MIME_TYPE;

    AttachmentusCFType(AttachmentusListener attachmentusListener, JiraAuthenticationContext jiraAuthenticationContext, DateTimeFormatterFactory dateTimeFormatterFactory, I18nResolver i18nResolver) {
        this.dateTimeFormatterFactory = dateTimeFormatterFactory;
        this.i18nResolver = i18nResolver;

        this.attachmentusListener = checkNotNull(attachmentusListener);
        this.jiraAuthenticationContext = checkNotNull(jiraAuthenticationContext);
    }

    @Override
    public String getStringFromSingularObject(String s) {
        return s;
    }

    @Override
    public String getSingularObjectFromString(String s) throws FieldValidationException {
        return s;
    }

    @Override
    public String getValueFromIssue(CustomField customField, Issue issue) {
        DateTimeFormatter dateTimeFormatter = null;
        try {
            dateTimeFormatter = getDateTimeFormatter();
        } catch (Exception e) {
            log.error("can't obtain dateTimeFormatter",e);
        }
        List<Map<String, Object>> attachmentsProps = attachmentusListener.sanitizeHasLastAttach(issue, jiraAuthenticationContext.getLoggedInUser(), false);
        if (attachmentsProps == null || attachmentsProps.isEmpty()) return null;
        StringBuilder result = new StringBuilder(i18nResolver.getText("ru.megaplan.jira.plugins.mps.attachmentus.nextfilewillbeattached")+"<br>");
        boolean hasSomeAttachments = false;
        for (Map<String,Object> attachmentProp : attachmentsProps) {
            String filename = null;
            Timestamp created = null;
            try {
                filename = attachmentProp.get(AttachmentusListener.A_FILENAME).toString();
                created = (Timestamp) attachmentProp.get(AttachmentusListener.A_CREATED);
            } catch (NullPointerException e) {
                log.error("filename or created do not filled",e);
                continue;
            } catch (ClassCastException e) {
                log.error("illegal type from attachProps",e);
                continue;
            }
            String createdString = null;
            if (dateTimeFormatter != null) {
                createdString = dateTimeFormatter.format(created);
            }
            result.append(filename + (createdString!=null?" : " + createdString:"") + "<br/>");
            hasSomeAttachments = true;
        }
        return hasSomeAttachments?result.toString():null;

    }

    private DateTimeFormatter getDateTimeFormatter() {
        DateTimeFormatter dateTimeFormatter = dateTimeFormatterFactory.formatter().forLoggedInUser();
        if (dateTimeFormatter == null) {
            log.error("can't obtain dateTimeFormatter with logged in user : "
                    + jiraAuthenticationContext.getLoggedInUser()!=null?jiraAuthenticationContext.getLoggedInUser().getName():null);
            return null;
        }
        dateTimeFormatter = dateTimeFormatter.withStyle(COMPLETE);
        return dateTimeFormatter;
    }
}
