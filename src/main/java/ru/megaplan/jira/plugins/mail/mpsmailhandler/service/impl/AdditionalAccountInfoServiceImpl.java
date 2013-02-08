package ru.megaplan.jira.plugins.mail.mpsmailhandler.service.impl;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.basic.DateConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import org.apache.log4j.Logger;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.AdditionalAccountInfoService;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util.CustomFieldMapperUtil;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util.CypherUtil;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.promo.Response;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 18.07.12
 * Time: 15:52
 * To change this template use File | Settings | File Templates.
 */
public class AdditionalAccountInfoServiceImpl implements AdditionalAccountInfoService {

    private final static Logger log = Logger.getLogger(AdditionalAccountInfoServiceImpl.class);

    private final static String PROTOCOL = "http://";
    private final static String SERVICEADDRESS = "www.megaplan.ru";
    private final static String SERVICEURI = "/PromoSaasApiV01/Account/supportInfo/";
    private final static String ACCESSID = "6553aabd976bbe320b13560";
    private final static String SECRETKEY = "e3b2fabde9cabd976bbe320be9d3cdedd996c4246a5919b2a12bdc0edd940c38";
    private final static String METHOD = "GET";
    private final static String DATEPATTERN = "EEE, dd MMM yyyy HH:mm:ss Z";
    private final static String ACCEPTTYPE = "application/json"; //but it is xml really

    public final static String BADRESPONSEMESSAGE = "bad response";

    private final CustomFieldManager customFieldManager;
    private final XStream xStream;

    public AdditionalAccountInfoServiceImpl(CustomFieldManager customFieldManager) {     // not work as expected. her s nim.
        this.customFieldManager = customFieldManager;
        xStream = makeXstream();
    }

    public AdditionalAccountInfoServiceImpl() {
        this.customFieldManager = ComponentAccessor.getComponentOfType(CustomFieldManager.class);
        xStream = makeXstream();
    }

    @Override
    public Map<CustomField, Object> getAdditionalAccountInfo(Long accountId) {
        String /*xml*/responseBody = getServiceResponse(accountId);
        Response parsedResponse = (Response) xStream.fromXML(responseBody);
        Map<CustomField, Object> result = CustomFieldMapperUtil.mapToCustomFields(parsedResponse,customFieldManager);
        return result;
    }


    private String getServiceResponse(Long accountId) {
        Client client = Client.create();
        String currentDateString = currentDate();
        String fullUri = SERVICEURI+accountId+".xml";
        String signature = CypherUtil.createSignature(SERVICEADDRESS + fullUri, SECRETKEY, METHOD, currentDateString);
        String requestAddress = PROTOCOL + SERVICEADDRESS + fullUri;
        WebResource webResource = client.resource(requestAddress);
        Map<String, String> headers = getHeaders(currentDateString, ACCEPTTYPE, ACCESSID + ":" + signature);
        WebResource.Builder builder = webResource.accept(ACCEPTTYPE);
        for (Map.Entry<String,String> e : headers.entrySet()) {
            builder = builder.header(e.getKey(),e.getValue());
        }
        ClientResponse clientResponse = builder.get(ClientResponse.class);
        if (clientResponse.getStatus() != 200) {
            StringBuilder hb = new StringBuilder();
            for (Map.Entry<String,String> e : headers.entrySet()) {
                hb.append(e.getKey()).append(':').append(e.getValue()).append('\n');
            }
            throw new IllegalStateException(BADRESPONSEMESSAGE + hb.toString());
        }
        return clientResponse.getEntity(String.class);
    }

    private Map<String, String> getHeaders(String currentDateString, String accepttype, String idAndSignature) {
        Map<String, String> result = new HashMap<String, String>();
        result.put("X-Sdf-Date",currentDateString);
        result.put("Date", currentDateString);
        result.put("Accept",accepttype);
        result.put("X-Authorization", idAndSignature);
        return result;
    }

    private String currentDate() {
        DateFormat dateFormat = new SimpleDateFormat(DATEPATTERN, Locale.US);
        Date currentDate = new Date();
        String currentDateString = dateFormat.format(currentDate);
        return currentDateString;
    }

    private XStream makeXstream() {
        XStream xStream = new XStream(new DomDriver()) {
            @Override
            protected MapperWrapper wrapMapper(MapperWrapper next) {
                return new MapperWrapper(next) {
                    @Override
                    public boolean shouldSerializeMember(Class definedIn,
                        String fieldName) {
                        if (definedIn == Object.class) {
                            return false;
                        }
                        return super.shouldSerializeMember(definedIn, fieldName);
                    }
                };
            }
        };


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
        return xStream;
    }

}
