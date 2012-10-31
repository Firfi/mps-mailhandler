package ru.megaplan.jira.plugins.mail.mpsmailhandler.message;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.security.auth.Subject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Enumeration;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 03.07.12
 * Time: 16:01
 * To change this template use File | Settings | File Templates.
 */
public class MessageSubjectWrapper extends Message {

    private final Message message;
    private String subject;

    public MessageSubjectWrapper(Message message, String subject) {
        this.message = message;
        this.subject = subject;
    }

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
    public Address[] getRecipients(RecipientType recipientType) throws MessagingException {
        return message.getRecipients(recipientType);
    }

    @Override
    public void setRecipients(RecipientType recipientType, Address[] addresses) throws MessagingException {
        message.setRecipients(recipientType, addresses);
    }

    @Override
    public void addRecipients(RecipientType recipientType, Address[] addresses) throws MessagingException {
        message.addRecipients(recipientType,addresses);
    }

    @Override
    public String getSubject() throws MessagingException {
        return subject;
    }

    @Override
    public void setSubject(String s) throws MessagingException {
        this.subject = s;
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
    public void setFlags(Flags flags, boolean b) throws MessagingException {
        message.setFlags(flags,b);
    }

    @Override
    public Message reply(boolean b) throws MessagingException {
        return message.reply(b);
    }

    @Override
    public void saveChanges() throws MessagingException {
        message.saveChanges();
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
    public boolean isMimeType(String s) throws MessagingException {
        return message.isMimeType(s);
    }

    @Override
    public String getDisposition() throws MessagingException {
        return message.getDisposition();
    }

    @Override
    public void setDisposition(String s) throws MessagingException {
        message.setDisposition(s);
    }

    @Override
    public String getDescription() throws MessagingException {
        return message.getDescription();
    }

    @Override
    public void setDescription(String s) throws MessagingException {
        message.setDescription(s);
    }

    @Override
    public String getFileName() throws MessagingException {
        return message.getFileName();
    }

    @Override
    public void setFileName(String s) throws MessagingException {
        message.setFileName(s);
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
    public void setDataHandler(DataHandler dataHandler) throws MessagingException {
        message.setDataHandler(dataHandler);
    }

    @Override
    public void setContent(Object o, String s) throws MessagingException {
        message.setContent(o,s);
    }

    @Override
    public void setText(String s) throws MessagingException {
        message.setText(s);
    }

    @Override
    public void setContent(Multipart multipart) throws MessagingException {
        message.setContent(multipart);
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException, MessagingException {
        message.writeTo(outputStream);
    }

    @Override
    public String[] getHeader(String s) throws MessagingException {
        return message.getHeader(s);
    }

    @Override
    public void setHeader(String s, String s1) throws MessagingException {
        message.setHeader(s,s1);
    }

    @Override
    public void addHeader(String s, String s1) throws MessagingException {
        message.addHeader(s,s1);
    }

    @Override
    public void removeHeader(String s) throws MessagingException {
        message.removeHeader(s);
    }

    @Override
    public Enumeration getAllHeaders() throws MessagingException {
        return message.getAllHeaders();
    }

    @Override
    public Enumeration getMatchingHeaders(String[] strings) throws MessagingException {
        return message.getMatchingHeaders(strings);
    }

    @Override
    public Enumeration getNonMatchingHeaders(String[] strings) throws MessagingException {
        return message.getNonMatchingHeaders(strings);
    }
}
