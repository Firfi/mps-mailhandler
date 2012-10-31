package ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.account;

import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.person.Person;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 02.07.12
 * Time: 13:19
 * To change this template use File | Settings | File Templates.
 */
public class Account {

    @XStreamAsAttribute
    private String id;
    @XStreamAsAttribute
    private String name;

    private String subject;
    private String question;
    private Type type;
    private Product product;
    private Browser browser;
    private Screen screen;
    private Typeticket typeticket;
    private Authorcategory authorcategory;
    private Person person;

    public Account(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Browser getBrowser() {
        return browser;
    }

    public void setBrowser(Browser browser) {
        this.browser = browser;
    }

    public Screen getScreen() {
        return screen;
    }

    public void setScreen(Screen screen) {
        this.screen = screen;
    }

    public Typeticket getTypeticket() {
        return typeticket;
    }

    public void setTypeticket(Typeticket typeticket) {
        this.typeticket = typeticket;
    }

    public Authorcategory getAuthorcategory() {
        return authorcategory;
    }

    public void setAuthorcategory(Authorcategory authorcategory) {
        this.authorcategory = authorcategory;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }
}
