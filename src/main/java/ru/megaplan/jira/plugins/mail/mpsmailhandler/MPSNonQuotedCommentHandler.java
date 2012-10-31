package ru.megaplan.jira.plugins.mail.mpsmailhandler;

import com.atlassian.jira.plugins.mail.handlers.NonQuotedCommentHandler;
import com.atlassian.plugin.util.ClassLoaderUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 28.08.12
 * Time: 20:39
 * To change this template use File | Settings | File Templates.
 */
public class MPSNonQuotedCommentHandler extends NonQuotedCommentHandler {

    private Collection messages;

    private static final String OUTLOOK_QUOTED_FILE = "outlook-email.translations";


    public String stripQuotedLines(String body, boolean isGmail)
    {
        if (body == null)
        {
            return null;
        }

        final StringTokenizer st = new StringTokenizer(body, "\n", true);
        final StringBuilder result = new StringBuilder();

        boolean strippedAttribution = false; // set to true once the attribution has been encountered
        boolean outlookQuotedLine = false; // set to true if the Microsoft Outlook reply message ("----- Original Message -----") is encountered.

        String line1;
        String line2 = null;
        String line3 = null;
        // Three-line lookahead; on each iteration, line1 may be added unless line2+line3 indicate it is an attribution
        do
        {
            line1 = line2;
            line2 = line3;
            line3 = (st.hasMoreTokens() ? st.nextToken() : null); // read next line
            if (!"\n".equals(line3))
            {
                // Ignore the newline ending line3, if line3 isn't a newline on its own
                if (st.hasMoreTokens())
                {
                    st.nextToken();
                }
            }
            if (!strippedAttribution)
            {
                if (!outlookQuotedLine)
                {
                    outlookQuotedLine = isOutlookQuotedLine(line1);
                }

                // Found our first quoted line; the attribution line may be line1 or line2
                if (isQuotedLine(line3))
                {
                    if (looksLikeAttribution(line1))
                    {
                        line1 = "> ";
                    }
                    else if (looksLikeAttribution(line2))
                    {
                        if (isGmail) line1 = "> ";
                        line2 = "> ";
                    }
                    strippedAttribution = true;
                }
            }
            if (line1 != null && !isQuotedLine(line1) && !outlookQuotedLine)
            {
                result.append(line1);
                if (!"\n".equals(line1))
                {
                    result.append("\n");
                }
            }
        }
        while (line1 != null || line2 != null || line3 != null);
        return result.toString();
    }

    private boolean looksLikeAttribution(String line)
    {
        boolean result = line != null && (line.endsWith(":") || line.endsWith(":\r"));
        return result;
    }

    private boolean isQuotedLine(String line)
    {
        return line != null && (line.startsWith(">") || line.startsWith("|"));
    }

    private boolean isOutlookQuotedLine(String line)
    {
        if (line != null)
        {
            for (Iterator iterator = getOutlookQuoteSeparators().iterator(); iterator.hasNext();)
            {
                String message = (String) iterator.next();
                if (line.indexOf(message) != -1)
                {
                    return true;
                }
            }
        }

        return false;
    }

    private Collection getOutlookQuoteSeparators()
    {
        if (messages == null)
        {
            messages = new LinkedList();
            BufferedReader reader = null;
            try
            {
                // The file is assumed to be UTF-8 encoded.
                reader = new BufferedReader(new InputStreamReader(ClassLoaderUtils.getResourceAsStream(OUTLOOK_QUOTED_FILE, NonQuotedCommentHandler.class), "UTF-8"));
                String message;
                while ((message = reader.readLine()) != null)
                {
                    messages.add(message);
                }
            }
            catch (IOException e)
            {
                // no more properties
                log.error("Error occurred while reading file '" + OUTLOOK_QUOTED_FILE + "'.");
            }
            finally
            {
                try
                {
                    if (reader != null)
                    {
                        reader.close();
                    }
                }
                catch (IOException e)
                {
                    log.error("Could not close the file '" + OUTLOOK_QUOTED_FILE + "'.");
                }
            }
        }
        return messages;
    }

}
