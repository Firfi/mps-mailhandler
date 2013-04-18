package ru.megaplan.jira.plugins.mail.mpsmailhandler.action;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.exception.RemoveException;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.jdbc.SQLProcessor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 2/26/13
 * Time: 2:07 AM
 * To change this template use File | Settings | File Templates.
 */
public class DeleteSpamAction extends JiraWebActionSupport {

    String sql = "select pkey, reporter from jiraissue where issuestatus = '10061'";

    public String doExecute() throws GenericEntityException, SQLException, RemoveException {
        IssueManager issueManager = ComponentAccessor.getIssueManager();
        UserManager userManager = getUserManager();
        UserUtil userUtil = ComponentAccessor.getUserUtil();
        User admin = userManager.getUser("megaplan");
        SQLProcessor p = new SQLProcessor("defaultDS");
        Set<String> users = new HashSet<String>();
        ResultSet rs = p.executeQuery(sql);
        while (rs.next()) {
            String issueKey = rs.getString(1);
            if (!issueKey.startsWith("MPS-")) continue;
            Issue issue = issueManager.getIssueObject(issueKey);
            issueManager.deleteIssue(getLoggedInUser(), issue, EventDispatchOption.DO_NOT_DISPATCH, false);
            String userName = rs.getString(2);
            users.add(userName);
        }
        for (String us : users) {
            User u = userManager.getUser(us);
            userUtil.removeUser(getLoggedInUser(), u);
        }
        return SUCCESS;
    }
}
