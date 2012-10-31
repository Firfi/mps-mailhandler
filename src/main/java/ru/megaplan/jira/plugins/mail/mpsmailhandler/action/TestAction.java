package ru.megaplan.jira.plugins.mail.mpsmailhandler.action;

import com.atlassian.core.AtlassianCoreException;
import com.atlassian.core.user.preferences.Preferences;
import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.watchers.WatcherManager;
import com.atlassian.jira.notification.NotificationRecipient;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.security.login.LoginManager;
import com.atlassian.jira.user.UserPropertyManager;
import com.atlassian.jira.user.preferences.JiraUserPreferences;
import com.atlassian.jira.user.preferences.PreferenceKeys;
import com.atlassian.jira.user.preferences.UserPreferencesManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import com.opensymphony.module.propertyset.PropertySet;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.basic.DateConverter;
import com.thoughtworks.xstream.converters.basic.NullConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.DomDriver;
import org.apache.commons.codec.binary.Base64;
import org.codehaus.jackson.map.ser.impl.PropertySerializerMap;
import org.ofbiz.core.entity.GenericEntityException;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.AdditionalAccountInfoService;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.impl.AdditionalAccountInfoServiceImpl;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util.CypherUtil;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.MPMessage;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.account.Account;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.account.Authorcategory;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.person.Person;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.promo.Response;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 02.07.12
 * Time: 13:43
 * To change this template use File | Settings | File Templates.
 */
public class TestAction extends JiraWebActionSupport {

    private final static String protocol = "http://";
    //private final static String serviceAddress = "www.prod.megatest.local";
    private final static String serviceAddress = "www.megaplan.ru";
    private final static String serviceUri = "/PromoSaasApiV01/Account/supportInfo/";
    private static Long accountId = 18659L;
    private final static String accessId = "6553aabd976bbe320b13560";
    private final static String secretKey = "e3b2fabde9cabd976bbe320be9d3cdedd996c4246a5919b2a12bdc0edd940c38";
    private final static String method = "GET";
    private final static String datePattern = "EEE, dd MMM yyyy HH:mm:ss Z";

    private String xml;

    private final AdditionalAccountInfoService additionalAccountInfoService;

    TestAction() {

        this.additionalAccountInfoService = new AdditionalAccountInfoServiceImpl();
    }

    public String doTestProperties() throws AtlassianCoreException {
        Collection<User> users = ComponentAccessor.getGroupManager().getUsersInGroup("email-accounts");
        UserPreferencesManager userPreferencesManager = ComponentAccessor.getUserPreferencesManager();
        StringBuilder sb = new StringBuilder();
        for (User user : users) {
            sb.append(user.getName()).append("<br>");
            Preferences preferences = userPreferencesManager.getPreferences(user);
            preferences.setBoolean(PreferenceKeys.USER_AUTOWATCH_DISABLED, true);
            preferences.setBoolean(PreferenceKeys.USER_NOTIFY_OWN_CHANGES, false);
            preferences.setString(PreferenceKeys.USER_NOTIFICATIONS_MIMETYPE, NotificationRecipient.MIMETYPE_HTML);
            sb.append(preferences.getBoolean(PreferenceKeys.USER_AUTOWATCH_DISABLED)).append("<br>");
            sb.append(preferences.getBoolean(PreferenceKeys.USER_NOTIFY_OWN_CHANGES)).append("<br>");
            sb.append(preferences.getString(PreferenceKeys.USER_NOTIFICATIONS_MIMETYPE)).append("<br>");

        }
        xml = sb.toString() + "sdasd";
        return SUCCESS;
    }

    public String doTestWatchers() throws GenericEntityException {
        WatcherManager watcherManager = ComponentAccessor.getWatcherManager();
        GroupManager groupManager = ComponentAccessor.getGroupManager();
        for (User user : groupManager.getUsersInGroup("email-accounts")) {
            watcherManager.removeAllWatchesForUser(user);
        }
        return SUCCESS;
    }

    @Override
    public String doExecute() throws URISyntaxException {
        try {
        Client client = Client.create();
        String currentDateString = currentDate();
        String fullUri = serviceUri+accountId+".xml";
        String signature = CypherUtil.createSignature(serviceAddress + fullUri, secretKey, method, currentDateString);//signature(currentDateString,serviceAddress + fullUri);
        WebResource webResource = client.resource(protocol + serviceAddress + fullUri);
        //WebResource webResource = client.resource("http://127.0.0.1:2990/jira/plugins/servlet/testrequestservlet");
         // neccessary for custom headers
        WebResource.Builder builder = webResource.accept("application/json")
                .header("X-Sdf-Date",currentDateString)
                .header("Date", currentDateString)
                .header("Accept","application/json")
                .header("X-Authorization",
                        accessId + ":" + signature);

        Map<CustomField, Object> m = additionalAccountInfoService.getAdditionalAccountInfo(accountId);
        log.warn(m.size());

       // webResource.accept("application/json");

        ClientResponse clientResponse = builder.get(ClientResponse.class);

        Response response = null;
        int statusCode = clientResponse.getStatus();
        String responseBody = clientResponse.getEntity(String.class);
        xml = responseBody;
        if (statusCode == 200) {
            XStream xStream = new XStream(new DomDriver());

            xStream.registerConverter(new DateConverter("yyyy-MM-dd",null){

                @Override
                public String toString(java.lang.Object obj) {
                    if (obj == null) return "";
                    return super.toString(obj);
                }

                @Override
                public Object fromString(String string) {
                    if (string == null || string.isEmpty()) return null;
                    else return super.fromString(string);
                }

            });

            xStream.registerConverter(new StringConverter() {
                @Override
                public String toString(java.lang.Object obj) {
                    if (obj == null) return "";
                    return super.toString(obj);
                }

                @Override
                public Object fromString(String string) {
                    if (string == null || string.isEmpty()) return null;
                    else return super.fromString(string);
                }
            });

            xStream.processAnnotations(Response.class);
            xml = responseBody;
            response = (Response) xStream.fromXML(responseBody);
        }
        } catch (Exception e) {
            accountId+=1;
            return doExecute();
        }
        return SUCCESS;
    }

    private String currentDate() {
        DateFormat dateFormat = new SimpleDateFormat(datePattern, Locale.US);
        Date currentDate = new Date();
        String currentDateString = dateFormat.format(currentDate);
        return currentDateString;
    }


    private String signature(String currentDateString,String uri) {
        StringBuilder uncoded = new StringBuilder();
        uncoded.append(method).append('\n');
        uncoded.append('\n');
        uncoded.append('\n');
        uncoded.append(currentDateString).append('\n');
        uncoded.append(uri);
        String uncodedString = uncoded.toString();
        byte[] digest;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec secret = new SecretKeySpec(secretKey.getBytes(),"HmacSHA1");
            mac.init(secret);
            digest = mac.doFinal(uncodedString.getBytes());
            StringWriter hexDigestWriter = new StringWriter();
            for (byte b : digest) {
                hexDigestWriter.write(String.format("%02x", b));
            }
            String hexDigest = hexDigestWriter.toString();
            digest = hexDigest.getBytes();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("it seems i can't find HmacSHA1");
        } catch (InvalidKeyException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            throw new RuntimeException(e);
        }

        String result = new String(Base64.encodeBase64(digest)).trim();
        return result;
    }


    public String getXml() {
        return xml;
    }


}
