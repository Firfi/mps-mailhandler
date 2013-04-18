package ru.megaplan.jira.plugins.mail.mpsmailhandler.service.util;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.priority.Priority;
import org.apache.log4j.Logger;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.CreateMPIssueHandler;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.MPMessage;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.promo.Response;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 18.07.12
 * Time: 16:30
 * To change this template use File | Settings | File Templates.
 */
public class CustomFieldMapperUtil {

    private final static Logger log = Logger.getLogger(CustomFieldMapperUtil.class);

    public static Map<CustomField, Object> mapToCustomFields(Response parsedResponse, CustomFieldManager customFieldManager) {
        Map<CustomField, Object> results = new HashMap<CustomField, Object>();
        Date expireDate = parsedResponse.getMegaplan().getExpiredate();
        if (expireDate != null) {
            CustomField cf = customFieldManager.getCustomFieldObjectByName(ExpireDate.CFNAME);
            if (cf == null) {
                log.error("cf with name : " + ExpireDate.CFNAME + " not found");
            } else {
                results.put(cf, new java.sql.Date(expireDate.getTime())); // java.util.date not supported
            }
        } else {
            log.info("expired date is null; acc id : " + parsedResponse.getMegaplan().getAccount().getId());
        }
        String product = parsedResponse.getMegaplan().getProduct();
        if (product != null) {
            CustomField cf = customFieldManager.getCustomFieldObjectByName(Product.CFNAME);
            if (cf == null) {
                log.error("cf with name : " + Product.CFNAME + " not found");
            } else {
                results.put(cf, product);
            }
        } else {
            log.info("product is null for account with id : " + parsedResponse.getMegaplan().getAccount().getId());
        }
        String sla = parsedResponse.getMegaplan().getSla();
        if (sla != null) {
            CustomField cf = customFieldManager.getCustomFieldObjectByName(SLA.CFNAME);
            if (cf == null) {
                log.error("cf with name : " + SLA.CFNAME + " not found");
            } else {
                results.put(cf, sla);
            }
        } else {
            log.info("sla is null for account with id : " + parsedResponse.getMegaplan().getAccount().getId());
        }
        String serviceCompany = parsedResponse.getMegaplan().getServicecompany();
        if (serviceCompany != null) {
            CustomField cf = customFieldManager.getCustomFieldObjectByName(ServiceCompany.CFNAME);
            if (cf == null) {
                log.error("cf with name : " + ServiceCompany.CFNAME + " not found");
            } else {
                results.put(cf, serviceCompany);
            }
        } else {
            log.info("serviceCompany is null for account with id : " + parsedResponse.getMegaplan().getAccount().getId());
        }
        String farm = parsedResponse.getMegaplan().getFarm();
        if (farm != null) {
            CustomField cf = customFieldManager.getCustomFieldObjectByName(Farm.CFNAME);
            if (cf == null) {
                log.error("cf with name : " + Farm.CFNAME + " not found");
            } else {
                results.put(cf, farm);
            }
        }
        return results;
    }

    public static class FieldMapper {


        private OptionsManager optionsManager;
        private CustomFieldManager customFieldManager;

        public MutableIssue setCustomFields(MutableIssue issueObject, MPMessage mpMessage, Map<CustomField, Object> additional) {
            if (customFieldManager == null) customFieldManager = ComponentAccessor.getCustomFieldManager();
            if (optionsManager == null) optionsManager = ComponentAccessor.getOptionsManager();
            try {
                String type = mpMessage.getAccount().getType().getName();
                log.debug("setting type : " + type);
                AccountType accountType
                        = AccountType.valueOf(type.toUpperCase());
                if (accountType != null) {
                    CustomField cf = getStrictCf(AccountType.CFNAME);
                    setOption(issueObject, cf, accountType.getName());
                }
            } catch (Exception e) {
                log.error("exception in custom field : type",e);
            }
            try {
                String productVersion = mpMessage.getAccount().getProduct().getFullversion(); // full!
                setSimpleCustomField(issueObject, ProductVersion.CFNAME,  productVersion);
            } catch (Exception e) {
                log.error("exception in custom field : product version",e);
            }
            try {
                String browser = mpMessage.getAccount().getBrowser().getName(); // and OS
                setSimpleCustomField(issueObject, Browser.CFNAME, browser);
            } catch (Exception e) {
                log.error("exception in custom field : browser",e);
            }
            try {
                String tickettype = mpMessage.getAccount().getTypeticket().getName();
                log.debug("setting type : " + tickettype);
                TicketType ticketType
                        = TicketType.valueOf(tickettype.toUpperCase());
                if (ticketType != null) {
                    CustomField cf = getStrictCf(TicketType.CFNAME);
                    setOption(issueObject, cf, ticketType.getName());
                }
            } catch (Exception e) {
                log.error("exception in custom field : ticketType",e);
            }
            try {
                String accountName = mpMessage.getAccount().getName(); // login
                setSimpleCustomField(issueObject, AccountName.CFNAME, accountName);
            } catch (Exception e) {
                log.error("exception in custom field : accountName",e);
            }
            try {
                String accountID = mpMessage.getAccount().getId();
                Double accountIdDouble = Double.parseDouble(accountID);
                setSimpleCustomField(issueObject, AccountID.CFNAME, accountIdDouble);
            } catch (Exception e) {
                log.error("exception in custom field : accountID",e);
            }
            try {
                String phone = mpMessage.getAccount().getPerson().getPhone(); // phone can be null
                if (!(phone == null || phone.isEmpty())) {
                    setSimpleCustomField(issueObject, Phone.CFNAME, phone);
                }
            } catch (Exception e) {
                log.error("exception in custom field : phone",e);
            }
            try {
                String reportertype = mpMessage.getAccount().getAuthorcategory().getName();
                ReporterType reporterType
                        = ReporterType.valueOf(reportertype.toUpperCase());
                if (reporterType == null) {
                    throw new Exception("reporter type not found for value : " + reportertype);
                }
                CustomField cf = getStrictCf(ReporterType.CFNAME);
                setOption(issueObject, cf, reporterType.getName());
            } catch (Exception e) {
                log.error("exception in custom field : ticketType",e);
            }
            try {
                String accountId = mpMessage.getAccount().getId();
                if (accountId == null || accountId.isEmpty()) {
                    String error = "account id in xml is empty"; //with php boys that can be
                    log.error(error);
                    throw new Exception(error);
                }

            } catch (Exception e) {
                log.error("exception in custom field : accoundId",e);
            }

            if (additional != null) {
                for (Map.Entry<CustomField, Object> e : additional.entrySet()) {
                    try {
                        if (e.getKey() == null || e.getValue() == null || (e.getValue() instanceof String && ((String)e.getValue()).isEmpty())) {
                            log.error("this can't be here : " + issueObject.getKey());
                            continue;
                        }
                        if (e.getKey().getName().equals(Product.CFNAME)) {
                            String productName = e.getValue().toString();
                            Product product
                                    = Product.valueOf(productName.toUpperCase());
                            if (product == null) {
                                throw new Exception("product type not found for value : " + productName);
                            }
                            setOption(issueObject, e.getKey(), product.getName());
                        } else if (e.getKey().getName().equals(SLA.CFNAME)) {
                            String slaName = e.getValue().toString();
                            SLA sla = SLA.valueOf(slaName.toUpperCase());
                            if (sla == null) {
                                throw new Exception("sla type not found for value : " + slaName);
                            }
                            setOption(issueObject, e.getKey(), sla.getName());
                            ConstantsManager constantsManager = ComponentAccessor.getConstantsManager();
                            Priority priority = constantsManager.getDefaultPriorityObject();
                            Collection<Priority> cp = constantsManager.getPriorityObjects();
                            for (Priority p : cp) {
                                if (sla.getPriority().equalsIgnoreCase(p.getName())) {
                                    priority = p;
                                    break;
                                }
                            }
                            if (priority != null) issueObject.setPriorityObject(priority);
                        } else if (e.getKey().getName().equals(ServiceCompany.CFNAME)) {
                            String serviceCompanyName = e.getValue().toString();
                            ServiceCompany serviceCompany;
                            String scName;
                            try {
                                serviceCompany = ServiceCompany.valueOf(serviceCompanyName.toUpperCase());
                                scName = serviceCompany.getName();
                            } catch (IllegalArgumentException ex) {
                                scName = ServiceCompany.MEGAPLAN.getName();
                            }
                            setOption(issueObject, e.getKey(), scName);
                        } else {
                            setSimpleCustomField(issueObject, e.getKey().getNameKey(), e.getValue());
                        }
                    } catch (Exception e1) {
                        log.error("some error in custom field : " + e.getKey().getName() + " in issue : " + issueObject.getKey(), e1);
                    }
                }
            }

            return issueObject;
        }

        private void setSimpleCustomField(MutableIssue issueObject, String cfName, Object value) throws Exception {
            CustomField cf = getStrictCf(cfName);
            if (value == null) {
                throw new Exception("value is not set in xml");
            }
            issueObject.setCustomFieldValue(cf, value);
        }

        private CustomField getStrictCf(String cfName) throws Exception {
            CustomField cf = customFieldManager.getCustomFieldObjectByName(cfName);
            if (cf == null) {
                throw new Exception("cannot find strict customfield with name : " + cfName);
            }
            return cf;
        }


        private void setOption(MutableIssue issueObject, CustomField cf, String value) throws Exception {
            Option option = optionsManager.getOptions(cf.getRelevantConfig(issueObject)).getOptionForValue(value, null);
            if (option == null) {
                throw new Exception("can't find option : " + value + " for cf : " + cf.getName());
            }
            issueObject.setCustomFieldValue(cf,option);
        }
    }

    public enum AccountType {
        SAAS_PAYED("SaaS paid"), SAAS_FREE("Saas free"), SAAS_TEST("SaaS test"), BOX("Box");
        public static final String CFNAME = "Решение"; //11562L; // Решение
        private final String name;
        AccountType(String name) {
            this.name = name;
        }
        private String getName() {
            return name;
        }
    }

    public enum Product {
        TASKLITE("Таск-менеджер Лайт"), TASKFREE("Таск-менеджер Лайт (Бесплатный)"), TASK("Таск-менеджер"), CRM("CRM Лайт"),
        CRMPRO("CRM"), INTRANET("Совместная работа (старая цена)"), CRMFREE("CRM (Бесплатный)"), FULL("Бизнес-менеджер (старая цена)"),
        FINANCE("Мегаплан Финансы"),
        PROJECT("Проект-менеджер"),
        BUSINESSFREE("Бизнес-менеджер Free"),
        CRM2012("CRM: Клиенты и продажи"), CRMFREE2012("CRM: Клиенты и продажи Free"),
        BUSINESS2012("Бизнес-менеджер"), COLLABORATION2012("Совместная работа"),
        COLLABORATIONFREE2012("Совместная работа Free"),
        BUSINESSFROMCRM("Бизнес-менеджер (цена CRM lite)"), BUSINESSFROMCRM2012("Бизнес-менеджер (цена CRM 2012)"), BUSINESSFROMCRMPRO("Бизнес-менеджер (старая цена CRM)");
        public static final String CFNAME = "Продукт"; // 12269L; // Продукт
        private final String name;
        Product(String name) {
            this.name = name;
        }
        private String getName() {
            return name;
        }
    }

    public enum ProductVersion {
        FAKEVALUE;
        public static final String CFNAME = "Версия продукта"; // 11264L;
    }

    public enum Browser {
        FAKEVALUE;
        public static final String CFNAME = "Версия браузера"; //11268L;
    }

    public enum TicketType {
        BUG("Сообщение об ошибке"), FUNCTIONAL("Помощь по функционалу"), PAY("Вопрос по оплате"), OTHER("Другой");
        public static final String CFNAME = "Тип запроса";
        private final String name;
        TicketType(String name) {
            this.name = name;
        }
        private String getName() {
            return name;
        }
    }

    public enum     ServiceCompany {
        MEGAPLAN("Россия"), UKRAINE("Украина"), BELARUS("Беларусь"), KAZAKHSTAN("Казахстан"),
        PARTNERS("Партнёр"), IQLINE("Марс");
        public static final String CFNAME = "MPS Обслуживающая компания";
        private final String name;
        ServiceCompany(String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
        @Nullable
        public static ServiceCompany getByName(String name) {
            for (ServiceCompany serviceCompany : ServiceCompany.values()) {
                if (serviceCompany.getName().equals(name)) {
                    return serviceCompany;
                }
            }
            return null;
        }
    }

    public enum AccountName {
        FAKEVALUE;
        public static final String CFNAME = "Название аккаунта"; //11261L;
    }

    public enum AccountID {
        FAKEVALUE;
        public static final String CFNAME = "MPS Account ID";
    }

    public enum Phone {
        FAKEVALUE;
        public static final String CFNAME = "Телефон"; //11271L;
    }

    public enum ReporterType {
        SUPPORT_MANAGER("Менеджер продаж"), DP_MANAGER("Менеджер ДП"), SALES_MANAGER("Менеджер продаж"), CLIENT("Клиент");
        public static final String CFNAME = "Создатель запроса";//12969L; // Создатель запроса
        private final String name;
        ReporterType(String name) {
            this.name = name;
        }
        private String getName() {
            return name;
        }

    }

    public enum SLA {
        SILVER("Silver", "Standard"),GOLD("Gold", "Major"),PLATINUM("Platinum", "Critical");
        public static final String CFNAME = "SLA";// = 12266L; // SLA
        private final String name;
        private final String priority;
        SLA(String name, String priority) {
            this.name = name;
            this.priority = priority;
        }
        public String getName() {
            return name;
        }
        public String getPriority() {
            return priority;
        }
    }

    public enum ExpireDate {
        FAKEVALUE;
        public static final String CFNAME = "Дата окончания поддержки";
    }

    public enum Farm {
        FAKEVALUE;
        public static final String CFNAME = "Ферма";
    }

}
