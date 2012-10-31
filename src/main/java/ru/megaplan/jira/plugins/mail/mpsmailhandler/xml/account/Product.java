package ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.account;

import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 02.07.12
 * Time: 13:34
 * To change this template use File | Settings | File Templates.
 */
public class Product {
    @XStreamAsAttribute
    private String name;
    @XStreamAsAttribute
    private String version;
    @XStreamAsAttribute
    private String fullversion;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFullversion() {
        return fullversion;
    }

    public void setFullversion(String fullversion) {
        this.fullversion = fullversion;
    }
}
