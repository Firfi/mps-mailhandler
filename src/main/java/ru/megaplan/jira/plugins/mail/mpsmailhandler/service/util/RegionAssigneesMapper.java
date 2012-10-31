package ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.user.util.UserManager;
import org.apache.commons.collections.map.MultiKeyMap;
import org.apache.log4j.Logger;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.rmi.MarshalledObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 27.08.12
 * Time: 11:27
 * To change this template use File | Settings | File Templates.
 */
public enum RegionAssigneesMapper {

    INSTANCE;

    private final static Logger log = Logger.getLogger(RegionAssigneesMapper.class);

    private enum Region {
        UKRAINE, BELARUS, KAZAKHSTAN
    }

    private final static String ASSIGNEE_UA = "a.belov";
    private final static String ASSIGNEE_BY = "s.bogomaz";
    private final static String ASSIGNEE_KZ = "t.sarsenbaev";

    private final Map<String, Region> emailToRegionMapping = new LinkedHashMap<String, Region>();
    private final Map<String, Region> serviceCompanyToRegionMapping = new HashMap<String, Region>();
    private final Map<Region, String> regionToAssigneeMapper = new HashMap<Region, String>();

    private RegionAssigneesMapper() {

        emailToRegionMapping.put("support@megaplan.ua", Region.UKRAINE);
        emailToRegionMapping.put("suport@megaplan.ua", Region.UKRAINE);
        emailToRegionMapping.put("support@megaplan.in.ua", Region.UKRAINE);
        emailToRegionMapping.put("suport@megaplan.in.ua", Region.UKRAINE);

        emailToRegionMapping.put("support@megaplan.by", Region.BELARUS);

        //emailToRegionMapping.put("support@megaplan.kz", Region.KAZAKHSTAN); die hindu

        serviceCompanyToRegionMapping.put(CustomFieldMapperUtil.ServiceCompany.UKRAINE.getName(), Region.UKRAINE);
        serviceCompanyToRegionMapping.put(CustomFieldMapperUtil.ServiceCompany.BELARUS.getName(), Region.BELARUS);
        //serviceCompanyToRegionMapping.put(CustomFieldMapperUtil.ServiceCompany.KAZAKHSTAN.getName(), Region.KAZAKHSTAN);

        regionToAssigneeMapper.put(Region.UKRAINE, ASSIGNEE_UA);
        regionToAssigneeMapper.put(Region.BELARUS, ASSIGNEE_BY);
        //regionToAssigneeMapper.put(Region.KAZAKHSTAN, ASSIGNEE_KZ);

    }

    public String getEmailForServiceCompany(String serviceCompany) {
        Region region = serviceCompanyToRegionMapping.get(serviceCompany);
        if (region == null) return null;
        String email = null;
        for (Map.Entry<String, Region> e : emailToRegionMapping.entrySet()) {
            if (region.equals(e.getValue())) {
                email = e.getKey();
                break;
            }
        }
        return email;
    }

    //first check for servicecompany cf, then for recipients
    public void setRegionAssignee(MutableIssue issueObject, Message message) throws MessagingException {
        CustomFieldManager customFieldManagerManager = ComponentAccessor.getCustomFieldManager();
        CustomField serviceCompanyCf = customFieldManagerManager.getCustomFieldObjectByName(CustomFieldMapperUtil.ServiceCompany.CFNAME);
        if (serviceCompanyCf == null) {
            log.error("can't find customfield with name: " + CustomFieldMapperUtil.ServiceCompany.CFNAME);
        }
        OptionsManager optionsManager = ComponentAccessor.getOptionsManager();
        UserManager userManager = ComponentAccessor.getUserManager();
        User assigneeToSet = null;
        CustomFieldMapperUtil.ServiceCompany serviceCompany = null;
        if (serviceCompanyCf == null) {
            log.error("cant find service company cf (name : " + CustomFieldMapperUtil.ServiceCompany.CFNAME + ")");
        } else {
            Object serviceCompanyObject = issueObject.getCustomFieldValue(serviceCompanyCf);
            if (serviceCompanyObject != null) {
                String serviceCompanyName = serviceCompanyObject.toString();
                serviceCompany = CustomFieldMapperUtil.ServiceCompany.getByName(serviceCompanyName);
            }

        }
        if (serviceCompany != null) {
            Region region = serviceCompanyToRegionMapping.get(serviceCompany.getName());
            if (region != null) {
                String assigneeName = regionToAssigneeMapper.get(region);
                if (assigneeName != null) {
                    assigneeToSet = userManager.getUser(assigneeName);
                    if (assigneeToSet != null) {
                        issueObject.setAssignee(assigneeToSet);
                        return;
                    }
                } else {
                    log.error("cant' find user with name : " + assigneeName);
                }
            }
        }

        if (message instanceof MimeMessage) {
            MimeMessage mimeMessage = (MimeMessage) message;
            Address[] recipients = mimeMessage.getRecipients(Message.RecipientType.TO);
            if (recipients != null && recipients.length != 0) {
                recipientLoop: for (Address recipient : recipients) {
                    if (recipient.toString() == null) continue;
                    for (Object email : emailToRegionMapping.keySet()) {
                        if (recipient.toString().contains(email.toString())) {
                            Region region = (Region) emailToRegionMapping.get(email.toString());
                            if (region != null) {
                                String regionAssigneeName = regionToAssigneeMapper.get(region);
                                if (regionAssigneeName == null) continue;
                                User u = userManager.getUser(regionAssigneeName);
                                if (u == null) {
                                    log.error("user : " + regionAssigneeName + " don't exist. check your codes.");
                                    continue;
                                }
                                assigneeToSet = u;
                                issueObject.setAssignee(assigneeToSet);
                                if (serviceCompanyCf != null) {
                                    try {
                                        Options options = optionsManager.getOptions(serviceCompanyCf.getRelevantConfig(issueObject));
                                        for (Map.Entry<String, Region> e : serviceCompanyToRegionMapping.entrySet()) {
                                            if (region.equals(e.getValue())) {
                                                String serviceCompanyToSet = e.getKey();
                                                Option option = options.getOptionForValue(serviceCompanyToSet, null);
                                                if (option == null) {
                                                    throw new Exception("can't find option : " + serviceCompanyToSet + " for cf : " + serviceCompanyCf.getName());
                                                }
                                                issueObject.setCustomFieldValue(serviceCompanyCf,option);
                                                break;
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.error("exception in setting option",e);
                                        return;
                                    }
                                }
                                break recipientLoop;
                            }
                        }
                    }
                }
            }
        }
    }
}
