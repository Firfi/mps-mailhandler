package ru.megaplan.jira.plugins.mail.mpsmailhandler.servlet;

import org.apache.log4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Writer;
import java.util.Enumeration;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 18.07.12
 * Time: 14:55
 * To change this template use File | Settings | File Templates.
 */
public class TestRequestServlet extends HttpServlet {

    private final static Logger log = Logger.getLogger(TestRequestServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, java.io.IOException {
        String line = null;
        Writer responseWriter = resp.getWriter();
        Enumeration headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            Object headerNameObject = headerNames.nextElement();
            log.warn(headerNameObject + " : " + req.getHeader(headerNameObject.toString()));
        }
        do {
            line = req.getReader().readLine();
            if (line != null) responseWriter.write(line);
            log.warn(line);
        } while (line != null);
    }

}
