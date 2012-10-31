package ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.person;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 02.07.12
 * Time: 13:20
 * To change this template use File | Settings | File Templates.
 */
public class Person {
    private String firstname;
    private String lastname;
    private String phone;
    private String email;

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
