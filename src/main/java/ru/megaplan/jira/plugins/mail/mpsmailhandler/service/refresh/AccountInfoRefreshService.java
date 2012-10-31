package ru.megaplan.jira.plugins.mail.mpsmailhandler.service.refresh;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.extension.Startable;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.service.JiraServiceContainer;
import com.atlassian.jira.service.ServiceManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.sal.api.lifecycle.LifecycleAware;
import com.atlassian.sal.api.scheduling.PluginScheduler;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.AdditionalAccountInfoService;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.refresh.job.RefreshTicketFieldsJob;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util.CustomFieldMapperUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 05.09.12
 * Time: 19:37
 * To change this template use File | Settings | File Templates.
 */
public class AccountInfoRefreshService implements LifecycleAware, DisposableBean {

    private final static Logger log = Logger.getLogger(AccountInfoRefreshService.class);

    private static final String JOB_NAME = AccountInfoRefreshService.class.getName() + ":job";

    private static final int interval = 1000*60*60*24;

    private static final String MAGAPLAN = "megaplan";

    private final static String[] closedStatuses = new String[] {"Закрыто", "Спам"};
    private final static String[] projects = new String[] {"MPS"};

    private final PluginScheduler pluginScheduler;
    private final SearchService searchService;
    private final CustomFieldManager customFieldManager;
    private final UserManager userManager;
    private final AdditionalAccountInfoService additionalAccountInfoService;
    private final ServiceManager serviceManager;



    public AccountInfoRefreshService(PluginScheduler pluginScheduler, SearchService searchService, CustomFieldManager customFieldManager, UserManager userManager, AdditionalAccountInfoService additionalAccountInfoService, ServiceManager serviceManager) {
        this.pluginScheduler = pluginScheduler;
        this.searchService = searchService;
        this.customFieldManager = customFieldManager;
        this.userManager = userManager;
        this.additionalAccountInfoService = additionalAccountInfoService;
        this.serviceManager = serviceManager;
    }

    @Override
    public void destroy() throws Exception {
        pluginScheduler.unscheduleJob(JOB_NAME);
    }

    @Override
    public void onStart() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("searchService",searchService);
        User initiator = userManager.getUser(MAGAPLAN);
        params.put("initiator", initiator);
        params.put("closedStatuses", closedStatuses);
        params.put("projects", projects);
        params.put("additionalAccountInfoService", additionalAccountInfoService);

        pluginScheduler.scheduleJob(JOB_NAME,
                RefreshTicketFieldsJob.class,
                params,
                new Date(),
                interval);
        JiraServiceContainer forebear = null;
        try {
            forebear = serviceManager.getServiceWithName(JOB_NAME);
        } catch (Exception e) {
            log.error("some exception on additional account info job initialisation", e);
        }
        if (forebear != null) {
            pluginScheduler.unscheduleJob(JOB_NAME);
        }
        serviceManager.refreshAll();

    }

}
