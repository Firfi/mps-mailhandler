package ru.megaplan.jira.plugins.mail.mpsmailhandler.service;

import com.atlassian.jira.issue.fields.CustomField;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 18.07.12
 * Time: 15:51
 * To change this template use File | Settings | File Templates.
 */
public interface AdditionalAccountInfoService {
    Map<CustomField, Object> getAdditionalAccountInfo(Long accountId);
}
