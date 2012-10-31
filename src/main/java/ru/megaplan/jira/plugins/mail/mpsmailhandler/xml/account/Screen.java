package ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.account;

import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 02.07.12
 * Time: 13:34
 * To change this template use File | Settings | File Templates.
 */
public class Screen {
    @XStreamAsAttribute
    private String resolution;

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }
}
