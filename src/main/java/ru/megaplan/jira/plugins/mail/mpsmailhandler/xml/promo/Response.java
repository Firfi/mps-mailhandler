package ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.promo;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 18.07.12
 * Time: 11:34
 * To change this template use File | Settings | File Templates.
 */
@XStreamAlias("response")
public class Response {

    private Status status;
    private Megaplan megaplan;

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Megaplan getMegaplan() {
        return megaplan;
    }

    public void setMegaplan(Megaplan megaplan) {
        this.megaplan = megaplan;
    }
}
