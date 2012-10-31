package ru.megaplan.jira.plugins.mail.mpsmailhandler.service.refresh.job;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.ModifiedValue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.issue.index.IssueIndexManager;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.util.ImportUtils;
import com.atlassian.jira.util.NotNull;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import com.atlassian.sal.api.scheduling.PluginJob;
import org.apache.log4j.Logger;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.AdditionalAccountInfoService;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util.CustomFieldMapperUtil;

import java.sql.*;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 05.09.12
 * Time: 19:43
 * To change this template use File | Settings | File Templates.
 */
public class RefreshTicketFieldsJob implements PluginJob {

    private final static Logger log = Logger.getLogger(RefreshTicketFieldsJob.class);

    private AdditionalAccountInfoService additionalAccountInfoService;
    CustomFieldManager customFieldManager;

    @Override
    public void execute(Map<String, Object> params) {
        log.debug("RefreshTicketFieldsJob started");
        customFieldManager = checkNotNull(ComponentAccessor.getCustomFieldManager());
        SearchService searchService = (SearchService) checkNotNull(params.get("searchService"));
        additionalAccountInfoService = (AdditionalAccountInfoService) checkNotNull(params.get("additionalAccountInfoService"));
        User initiator = (User) checkNotNull(params.get("initiator"));
        String[] closedStatuses = (String[]) checkNotNull(params.get("closedStatuses"));
        String[] projects = (String[]) checkNotNull(params.get("projects"));
        CustomField accountIdCustomField = customFieldManager.getCustomFieldObjectByName(CustomFieldMapperUtil.AccountID.CFNAME);
        if (accountIdCustomField == null) {
            log.error("can't find custom field with name : " + CustomFieldMapperUtil.AccountID.CFNAME);
            return;
        }
        JqlQueryBuilder jqlQueryBuilder = JqlQueryBuilder.newBuilder();
        Query query = jqlQueryBuilder.where().project(projects).and().not().status(closedStatuses).and().customField(accountIdCustomField.getIdAsLong()).notEqEmpty().endWhere().buildQuery();
        List<Issue> foundIssues;
        try {
            SearchResults searchResults = searchService.search(initiator, query, PagerFilter.getUnlimitedFilter());
            foundIssues = searchResults.getIssues();
        } catch (SearchException e) {
            log.error("error in jql",e);
            return;
        }
        Map<Long, List<Issue>> issuesByAccountId = getIssuesByAccountId(foundIssues, accountIdCustomField);
        updateFields(issuesByAccountId);
    }



    private Map<Long, List<Issue>> getIssuesByAccountId(List<Issue> foundIssues, CustomField accountIdCustomField) {
        Map<Long, List<Issue>> result = new HashMap<Long, List<Issue>>();
        for (Issue issue : foundIssues) {
            Object accountIdObject = accountIdCustomField.getValue(issue);
            if (accountIdObject != null) {
                Long accountId = null;
                try {
                    accountId = (long)Double.parseDouble(accountIdObject.toString());
                } catch (NumberFormatException e) {
                    log.error("number format exception in parsing account id value : " + accountIdObject + " for issue : " + issue.getKey());
                    continue;
                }
                if (accountId != null) {
                    List<Issue> issuesForAccount = result.get(accountId);
                    if (issuesForAccount == null) {
                        issuesForAccount = new ArrayList<Issue>();
                        result.put(accountId, issuesForAccount);
                    }
                    issuesForAccount.add(issue);
                }
            }
        }
        return result;
    }

    private void updateFields(Map<Long, List<Issue>> issuesByAccountId) {
        CustomField expireDateCF = customFieldManager.getCustomFieldObjectByName(CustomFieldMapperUtil.ExpireDate.CFNAME);
        if (expireDateCF == null) {
            throw new RuntimeException("can't find customfield with name : " + CustomFieldMapperUtil.ExpireDate.CFNAME);
        }
        for (Map.Entry<Long, List<Issue>> entry : issuesByAccountId.entrySet()) {
            Long accountId = entry.getKey();
            Map<CustomField, Object> info = null;
            try {
                info = additionalAccountInfoService.getAdditionalAccountInfo(accountId);
            } catch (Exception e) {
                log.warn("service unavailable");
                return;
            }

            Object expireDateObject = info.get(expireDateCF);
            if (expireDateObject != null) {
                java.sql.Date expireDate = (java.sql.Date) expireDateObject; //we have here date object with time zone but service returns object without timezone, so
                long acceptedRange = 24*60*60*1000;
                List<Issue> updated = new ArrayList<Issue>();
                for (Issue i : entry.getValue()) {
                    log.debug("obtaining current expiring date");
                    java.util.Date currentExpireDate = (java.util.Date) expireDateCF.getValue(i);
                    log.debug("current expiring date from cf : " + currentExpireDate);
                    if (currentExpireDate == null || !(inRange(expireDate.getTime(), currentExpireDate.getTime(), acceptedRange))) {
                        log.debug("for issue : " + i.getKey() + " currentDate : " + currentExpireDate + " " + (currentExpireDate==null?null:currentExpireDate.getTime()) + " and expireDate : " + expireDate + " " + expireDate.getTime());
                        expireDateCF.updateValue(null, i, new ModifiedValue(currentExpireDate, expireDate), new DefaultIssueChangeHolder());
                        updated.add(i);
                    }
                }
                if (log.isDebugEnabled()) {
                    if (updated.isEmpty()) {
                        log.debug("updated issues for account id : " + accountId + " is empty");
                    } else {
                        log.debug("updated Issues for accountId : " + accountId + " : ");
                        for (Issue i : updated) {
                            log.debug(i.getKey());
                        }
                    }


                }

                indexIssues(updated);
            }
        }
    }

    private boolean inRange(long time1, long time2, long acceptedRange) {
        return ((Math.abs(time1-time2))<acceptedRange);
    }

    private static void indexIssues(@NotNull Collection<Issue> issues) {
        boolean oldValue = ImportUtils.isIndexIssues();
        ImportUtils.setIndexIssues(true);
        IssueIndexManager issueIndexManager = ComponentAccessor.getComponentOfType(IssueIndexManager.class);
        try {
            issueIndexManager.reIndexIssueObjects(issues);
        } catch (IndexException e) {
            log.error("Unable to index issues", e);
        }
        ImportUtils.setIndexIssues(oldValue);
    }

}
