package ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.promo;

import ru.megaplan.jira.plugins.mail.mpsmailhandler.xml.promo.account.Account;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 18.07.12
 * Time: 11:34
 * To change this template use File | Settings | File Templates.
 */
public class Megaplan {

    private Account account;
    private String sla;
    private String product;
    private String producttype;
    private String servicecompany;
    private Date expiredate;

    private String farm;

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public String getSla() {
        return sla;
    }

    public void setSla(String sla) {
        this.sla = sla;
    }
    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getProducttype() {
        return producttype;
    }

    public void setProducttype(String producttype) {
        this.producttype = producttype;
    }

    public String getServicecompany() {
        return servicecompany;
    }

    public void setServicecompany(String servicecompany) {
        this.servicecompany = servicecompany;
    }

    public Date getExpiredate() {
        return expiredate;
    }

    public void setExpiredate(Date expitedate) {
        this.expiredate = expitedate;
    }

    public String getFarm() {
        return farm;
    }

    public void setFarm(String farm) {
        this.farm = farm;
    }
}
