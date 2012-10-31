package it.ru.megaplan.jira.plugins.mail.mpsmailhandler;

import com.atlassian.jira.webtests.EmailFuncTestCase;
import com.atlassian.jira.webtests.JIRAServerSetup;
import com.atlassian.mail.Email;
import com.atlassian.mail.MailFactory;
import com.atlassian.mail.server.SMTPMailServer;
import com.icegreen.greenmail.util.GreenMail;
import it.util.MailServiceOverriden;
import org.apache.commons.io.IOUtils;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.lang.reflect.Field;
import java.net.BindException;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 24.07.12
 * Time: 15:09
 * To change this template use File | Settings | File Templates.
 */
public class CreateIssuesByMailTest extends EmailFuncTestCase {

    private final static String FROMEMAIL = "response@megaplan.ru";
    private final static String A_PATH = "account_prod_1000020.xml";

    protected MailServiceOverriden mailServiceOverriden;

    @Override
    protected void setUpTest()
    {
        super.setUpTest();
        mailServiceOverriden = new MailServiceOverriden(log);
        mailService = mailServiceOverriden;
    }


    public void testSimpleCreateIssueByMail() throws Exception {

        administration.restoreData("mailtest.xml");
        configureAndStartMailServers(FROMEMAIL, "MEGATEST", JIRAServerSetup.SMTP_POP3);
        navigation.gotoAdmin();
        tester.clickLink("incoming_mail");
        tester.assertTextNotPresent("You do not currently have any POP / IMAP servers configured");
        //SMTPMailServer smtpMailServer = getDefaultSMTPMailServer();
        DataSource source =
                new FileDataSource("src/test/resources/" + A_PATH);
        byte[] bytes = IOUtils.toByteArray(source.getInputStream());
        mailServiceOverriden.sendAttachmentMessage(FROMEMAIL, "testuser@pepyaka.ru", "test", "btest", bytes, "application/xml", A_PATH, "some attach");
        MimeMessage[] mimeMessages = mailService.getReceivedMessages();

        //smtpMailServer.send(email);
//        try {
//            Thread.sleep(100500L);
//        } catch (InterruptedException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        }
    }

    private Email getXMLEmail() throws MessagingException {
        Email email = new Email(FROMEMAIL);
        email.setFrom(FROMEMAIL);
        email.setSubject("test");
        email.setMultipart(getXMLMultipart());
        return email;
    }

    private SMTPMailServer getDefaultSMTPMailServer() {
        return MailFactory.getServerManager().getDefaultSMTPMailServer();
    }

    private Multipart getXMLMultipart() throws MessagingException {
        Multipart multipart = new MimeMultipart("related");
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("test");
        textPart.setHeader("Content-type", "text/plain");
        multipart.addBodyPart(textPart);
        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setFileName(A_PATH);
        DataSource source =
                new FileDataSource(A_PATH);
        attachmentPart.setDataHandler(
                new DataHandler(source));
        multipart.addBodyPart(attachmentPart);
        return multipart;
    }
}
