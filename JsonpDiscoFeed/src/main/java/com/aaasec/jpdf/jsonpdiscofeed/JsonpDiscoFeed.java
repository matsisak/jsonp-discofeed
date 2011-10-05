package com.aaasec.jpdf.jsonpdiscofeed;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.logging.Logger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** 
 *
 * @author Stefan Santesson, 3xA Security AB
 */
public class JsonpDiscoFeed extends HttpServlet {

    private ServletContext context;
    private static final Logger LOG = Logger.getLogger(JsonpDiscoFeed.class.getName());
    private static final String LF = System.getProperty("line.separator");
    private String metaCacheFileName;
    private MetaData metaData;
    private String jsonData;
    private String cacheDealyMinutes;
    private long lastCache;

    @Override
    public void init(ServletConfig config) throws ServletException {
        this.context = config.getServletContext();
        this.metaCacheFileName = config.getInitParameter("MetaDataCache");
        cacheDealyMinutes = config.getInitParameter("CacheRefreshMinutes");
        metaData = new MetaData(new File(metaCacheFileName));
        jsonData = metaData.getDiscoJson();
        lastCache = System.currentTimeMillis();
    }

    /** 
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String callback = request.getParameter("callback");
        if (callback == null) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            response.getWriter().write("");
            return;
        }

        response.setContentType("application/javascript");
        String json = getMetadataJson();
        String sourceUrl = request.getParameter("source");
        if (sourceUrl != null) {
            json = getDiscoFeed(sourceUrl);
        }
        String jsonp = callback + "(" + json + ")";
        response.getWriter().write(jsonp);

    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
     * Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
     * Returns a short description of the servlet.
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

    private String getDiscoFeed(String sourceUrl) {
        URL url;
        String json = "[]";
        try {
            url = new URL(sourceUrl);
            byte[] jsonBytes = Utils.getUrlBytes(url);
            if (jsonBytes != null) {
                json = new String(jsonBytes, Charset.forName("UTF-8"));
            }
        } catch (Exception ex) {
        }
        return json;
    }


    private String getMetadataJson() {
        Long currentTime = System.currentTimeMillis();
        long delay;
        try {
            delay = Long.decode(cacheDealyMinutes);
        } catch (Exception ex) {
            delay = 10;
        }
        delay = delay * 1000 * 60;

        if (currentTime > lastCache + delay) {
            // recache
            metaData = new MetaData(new File(metaCacheFileName));
        }
        return metaData.getDiscoJson();
    }
}