package ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.account;

import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 02.07.12
 * Time: 13:34
 * To change this template use File | Settings | File Templates.
 */
public class Browser {
    @XStreamAsAttribute
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
