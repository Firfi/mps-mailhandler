package ru.megaplan.jira.plugins.mail.mpsmailhandler.util;

import com.atlassian.mail.HtmlToTextConverter;
import com.atlassian.mail.MailUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import java.io.*;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 28.08.12
 * Time: 19:11
 * To change this template use File | Settings | File Templates.
 */
public class NotJiraMailUtils {

    private static final Logger log = Logger.getLogger(NotJiraMailUtils.class);

    private static final String DEFAULT_ENCODING = "ISO-8859-1";

    static final String MAILRUSUFFIX = "@mail.ru";
    static final String GMAILSUFFIX = "@gmail.com";
    static final String RAMBLERSUFFIX = "@rambler.ru";

    static final String MULTIPART_ALTERNATE_CONTENT_TYPE = "multipart/alternative";
    static final String TEXT_CONTENT_TYPE = "text/plain";
    static final String HTML_CONTENT_TYPE = "text/html";

    private static final NotJiraHtmlToTextConverter htmlConverter = new NotJiraHtmlToTextConverter();

    public static String getBody(Message message) throws MessagingException
    {
        try
        {
            String content = extractTextFromPart(message);

            boolean htmlFirst = htmlFormFirst(message); // chisti govno ya skazal

            if (content == null)
            {
                if (message.getContent() instanceof Multipart)
                {
                    content = getBodyFromMultipart((Multipart) message.getContent(), htmlFirst);
                }
            }

            if (content == null)
            {
                //didn't match anything above
                log.info("Could not find any body to extract from the message");
            }

            return content;
        }
        catch (ClassCastException cce)
        {
            log.info("Exception getting the content type of message - probably not of type 'String': " + cce.getMessage());
            return null;
        }
        catch (IOException e)
        {
            log.info("IOException whilst getting message content " + e.getMessage());
            return null;
        }
    }

    public static boolean isMailRu(Message message) throws MessagingException {
        return isSuffix(message, MAILRUSUFFIX);
    }

    public static boolean isGmail(Message message) throws MessagingException {
        return isSuffix(message, GMAILSUFFIX);
    }

    public static boolean isRambler(Message message) throws MessagingException {
        return isSuffix(message, RAMBLERSUFFIX);
    }

    public static boolean htmlFormFirst(Message message) throws MessagingException {
        return isMailRu(message) || isRambler(message);
    }

    private static boolean isSuffix(Message message, String suffix) throws MessagingException {
        Address[] from = message.getFrom();
        if (from == null || from.length == 0) return false;
        for (Address address : from) {
            if (address instanceof InternetAddress) {
                InternetAddress internetAddress = (InternetAddress) address;
                String stringAddress = internetAddress.getAddress();
                if (StringUtils.isEmpty(stringAddress)) continue;
                return (stringAddress.endsWith(suffix));
            }
        }
        return false;
    }

    private static String getBodyFromMultipart(Multipart multipart, boolean isHtml) throws MessagingException, IOException
    {
        StringBuffer sb = new StringBuffer();
        getBodyFromMultipart(multipart, sb, isHtml);
        return sb.toString();
    }

    private static void getBodyFromMultipart(Multipart multipart, StringBuffer sb, boolean isHtml) throws MessagingException, IOException
    {
        String multipartType = multipart.getContentType();

        // if an multipart/alternative type we just get the first text or html content found
        if(multipartType != null && compareContentType(multipartType, MULTIPART_ALTERNATE_CONTENT_TYPE))
        {
            if (isHtml) {
                BodyPart part = getFirstInlinePartWithMimeType(multipart, HTML_CONTENT_TYPE);

                if(part != null)
                {
                    log.warn("MailUtils.isPartHtml(part) : " + MailUtils.isPartHtml(part));
                    appendMultipartText(extractTextFromPart(part), sb);
                }
                else
                {
                    part = getFirstInlinePartWithMimeType(multipart, TEXT_CONTENT_TYPE);
                    appendMultipartText(extractTextFromPart(part), sb);
                }
            } else {
                BodyPart part = getFirstInlinePartWithMimeType(multipart, TEXT_CONTENT_TYPE);
                if(part != null)
                {
                    appendMultipartText(extractTextFromPart(part), sb);
                }
                else
                {
                    part = getFirstInlinePartWithMimeType(multipart, HTML_CONTENT_TYPE);
                    appendMultipartText(extractTextFromPart(part), sb);
                }
            }

            return;
        }

        // otherwise assume multipart/mixed type and construct the contents by retrieving all text and html
        for (int i = 0, n = multipart.getCount(); i < n; i++)
        {
            BodyPart part = multipart.getBodyPart(i);
            String contentType = part.getContentType();

            if (!Part.ATTACHMENT.equals(part.getDisposition()) && contentType != null)
            {
                try
                {
                    String content = extractTextFromPart(part);
                    if (content != null)
                    {
                        appendMultipartText(content, sb);
                    }
                    else if(part.getContent() instanceof Multipart)
                    {
                        getBodyFromMultipart((Multipart) part.getContent(), sb, isHtml);
                    }
                }
                catch (IOException exception)
                {
                    // We swallow the exception because we want to allow processing to continue
                    // even if there is a bad part in one part of the message
                    log.warn("Error retrieving content from part '" + exception.getMessage() + "'", exception);
                }
            }
        }
    }

    private static BodyPart getFirstInlinePartWithMimeType(Multipart multipart, String mimeType) throws MessagingException
    {
        for (int i = 0, n = multipart.getCount(); i < n; i++)
        {
            BodyPart part = multipart.getBodyPart(i);
            String contentType = part.getContentType();
            if (!Part.ATTACHMENT.equals(part.getDisposition()) && contentType != null && compareContentType(contentType, mimeType))
            {
                return part;
            }
        }
        return null;
    }

    private static boolean compareContentType(String contentType, String mimeType)
    {
        return contentType.toLowerCase().startsWith(mimeType);
    }

    private static void appendMultipartText(String content, StringBuffer sb) throws IOException, MessagingException
    {
        if (content != null)
        {
            if(sb.length() > 0) sb.append("\n");
            sb.append(content);
        }
    }

    private static String extractTextFromPart(Part part) throws IOException, MessagingException,
            UnsupportedEncodingException
    {
        if (part == null)
            return null;

        String content = null;

        if (MailUtils.isPartPlainText(part))
        {
            try
            {
                content = (String) part.getContent();
            }
            catch (UnsupportedEncodingException e)
            {
                // If the encoding is unsupported read the content with default encoding
                log.warn("Found unsupported encoding '" + e.getMessage() + "'. Reading content with "
                        + DEFAULT_ENCODING + " encoding.");
                content = getBody(part, DEFAULT_ENCODING);
            }
        }
        else if (MailUtils.isPartHtml(part))
        {
            log.warn("html shit : ");
            log.warn(part.getContent());
            content = htmlConverter.convert((String) part.getContent());
        }

        if (content == null)
        {
            log.warn("Unable to extract text from MIME part with Content-Type '" + part.getContentType());
        }

        return content;
    }

    private static String getBody(Part part, String charsetName) throws UnsupportedEncodingException,
            IOException, MessagingException
    {
        Reader input = null;
        StringWriter output = null;
        try
        {
            input = new BufferedReader(new InputStreamReader(part.getInputStream(), charsetName));
            output = new StringWriter();
            IOUtils.copy(input, output);
            return output.getBuffer().toString();
        }
        finally
        {
            IOUtils.closeQuietly(input);
            IOUtils.closeQuietly(output);
        }
    }



}
