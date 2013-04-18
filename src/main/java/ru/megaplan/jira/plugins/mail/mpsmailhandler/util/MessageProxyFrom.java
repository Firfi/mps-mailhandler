package ru.megaplan.jira.plugins.mail.mpsmailhandler.util;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.search.SearchTerm;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;

/**
 * Created with IntelliJ IDEA.
 * User: Firfi
 * Date: 2/20/13
 * Time: 4:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class MessageProxyFrom extends Message {
    private Message message;

    @Override
    public Address[] getFrom() throws MessagingException {
        return message.getFrom();
    }

    @Override
    public void setFrom() throws MessagingException {
        message.setFrom();
    }

    @Override
    public void setFrom(Address address) throws MessagingException {
        message.setFrom(address);
    }

    @Override
    public void addFrom(Address[] addresses) throws MessagingException {
        message.addFrom(addresses);
    }

    @Override
    public Address[] getRecipients(RecipientType type) throws MessagingException {
        return message.getRecipients(type);
    }

    @Override
    public Address[] getAllRecipients() throws MessagingException {
        return message.getAllRecipients();
    }

    @Override
    public void setRecipients(RecipientType type, Address[] addresses) throws MessagingException {
        message.setRecipients(type, addresses);
    }

    @Override
    public void setRecipient(RecipientType type, Address address) throws MessagingException {
        message.setRecipient(type, address);
    }

    @Override
    public void addRecipients(RecipientType type, Address[] addresses) throws MessagingException {
        message.addRecipients(type, addresses);
    }

    @Override
    public void addRecipient(RecipientType type, Address address) throws MessagingException {
        message.addRecipient(type, address);
    }

    @Override
    public Address[] getReplyTo() throws MessagingException {
        return message.getReplyTo();
    }

    @Override
    public void setReplyTo(Address[] addresses) throws MessagingException {
        message.setReplyTo(addresses);
    }

    @Override
    public String getSubject() throws MessagingException {
        return message.getSubject();
    }

    @Override
    public void setSubject(String subject) throws MessagingException {
        message.setSubject(subject);
    }

    @Override
    public Date getSentDate() throws MessagingException {
        return message.getSentDate();
    }

    @Override
    public void setSentDate(Date date) throws MessagingException {
        message.setSentDate(date);
    }

    @Override
    public Date getReceivedDate() throws MessagingException {
        return message.getReceivedDate();
    }

    @Override
    public Flags getFlags() throws MessagingException {
        return message.getFlags();
    }

    @Override
    public boolean isSet(Flags.Flag flag) throws MessagingException {
        return message.isSet(flag);
    }

    @Override
    public void setFlags(Flags flag, boolean set) throws MessagingException {
        message.setFlags(flag, set);
    }

    @Override
    public void setFlag(Flags.Flag flag, boolean set) throws MessagingException {
        message.setFlag(flag, set);
    }

    @Override
    public int getMessageNumber() {
        return message.getMessageNumber();
    }

    @Override
    public void setMessageNumber(int msgnum) {
        message.setMessageNumber(msgnum);
    }

    @Override
    public Folder getFolder() {
        return message.getFolder();
    }

    @Override
    public boolean isExpunged() {
        return message.isExpunged();
    }

    @Override
    public void setExpunged(boolean expunged) {
        message.setExpunged(expunged);
    }

    @Override
    public Message reply(boolean replyToAll) throws MessagingException {
        return message.reply(replyToAll);
    }

    @Override
    public void saveChanges() throws MessagingException {
        message.saveChanges();
    }

    @Override
    public boolean match(SearchTerm term) throws MessagingException {
        return message.match(term);
    }

    @Override
    public int getSize() throws MessagingException {
        return message.getSize();
    }

    @Override
    public int getLineCount() throws MessagingException {
        return message.getLineCount();
    }

    @Override
    public String getContentType() throws MessagingException {
        return message.getContentType();
    }

    @Override
    public boolean isMimeType(String mimeType) throws MessagingException {
        return message.isMimeType(mimeType);
    }

    @Override
    public String getDisposition() throws MessagingException {
        return message.getDisposition();
    }

    @Override
    public void setDisposition(String disposition) throws MessagingException {
        message.setDisposition(disposition);
    }

    @Override
    public String getDescription() throws MessagingException {
        return message.getDescription();
    }

    @Override
    public void setDescription(String description) throws MessagingException {
        message.setDescription(description);
    }

    @Override
    public String getFileName() throws MessagingException {
        return message.getFileName();
    }

    @Override
    public void setFileName(String filename) throws MessagingException {
        message.setFileName(filename);
    }

    @Override
    public InputStream getInputStream() throws IOException, MessagingException {
        return message.getInputStream();
    }

    @Override
    public DataHandler getDataHandler() throws MessagingException {
        return message.getDataHandler();
    }

    @Override
    public Object getContent() throws IOException, MessagingException {
        return message.getContent();
    }

    @Override
    public void setDataHandler(DataHandler dh) throws MessagingException {
        message.setDataHandler(dh);
    }

    @Override
    public void setContent(Object obj, String type) throws MessagingException {
        message.setContent(obj, type);
    }

    @Override
    public void setText(String text) throws MessagingException {
        message.setText(text);
    }

    @Override
    public void setContent(Multipart mp) throws MessagingException {
        message.setContent(mp);
    }

    @Override
    public void writeTo(OutputStream os) throws IOException, MessagingException {
        message.writeTo(os);
    }

    @Override
    public String[] getHeader(String header_name) throws MessagingException {
        return message.getHeader(header_name);
    }

    @Override
    public void setHeader(String header_name, String header_value) throws MessagingException {
        message.setHeader(header_name, header_value);
    }

    @Override
    public void addHeader(String header_name, String header_value) throws MessagingException {
        message.addHeader(header_name, header_value);
    }

    @Override
    public void removeHeader(String header_name) throws MessagingException {
        message.removeHeader(header_name);
    }

    @Override
    public Enumeration getAllHeaders() throws MessagingException {
        return message.getAllHeaders();
    }

    @Override
    public Enumeration getMatchingHeaders(String[] header_names) throws MessagingException {
        return message.getMatchingHeaders(header_names);
    }

    @Override
    public Enumeration getNonMatchingHeaders(String[] header_names) throws MessagingException {
        return message.getNonMatchingHeaders(header_names);
    }
}
