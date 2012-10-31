package ru.megaplan.jira.plugins.mail.mpsmailhandler.xml;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.account.Account;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.person.Person;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 02.07.12
 * Time: 13:13
 * To change this template use File | Settings | File Templates.
 */
@XStreamAlias("megaplan")
public class MPMessage {
    private Account account;

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

}
