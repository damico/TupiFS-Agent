package org.jdamico.tupifs.dproxy.web;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.jdamico.tupifs.dproxy.runtime.StartDProxy;

/**
 * Servlet implementation class Connector
 */

public class Connector extends HttpServlet {



    // ### FIELDS ##################################################################################

    /**
     * Serialization UID.
     */
    private static final long serialVersionUID = 109232321023L;

    /**
     * Key for redirect location header.
     */
    private static final String LOCATION_HEADER = "Location";

    /**
     * Key for content type header.
     */
    private static final String CONTENT_TYPE_HEADER_NAME = "Content-Type";

    /**
     * Key for content length header.
     */
    private static final String CONTENT_LENGTH_HEADER_NAME = "Content-Length";

    /**
     * Key for host header
     */
    private static final String HOST_HEADER_NAME = "Host";

    /**
     * The directory to use to temporarily store uploaded files
     */
    private static final File UPLOAD_TEMP_DIRECTORY = new File(System.getProperty("java.io.tmpdir"));
    
    // Proxy host params

    /**
     * The host to which we are proxying requests
     */
    private String proxyHost = StartDProxy.proxyHost;

    /**
     * The port on the proxy host to wihch we are proxying requests. Default value is 80.
     */
    private int proxyPort = StartDProxy.srvPort;

    /**
     * The maximum size for uploaded files in bytes. Default value is 5MB.
     */
    private int maxFileUploadSize = 5 * 1024 * 1024;

    /**
     * The (optional) protocol name http or https.
     */
    private String protocol = "protocol";

    
    public Connector() {
        super();
    }


    // ### METHODS #################################################################################

    // --- [init] ----------------------------------------------------------------------------------

    /**
     * Initialize the <code>Connector</code>
     * @param servletConfig The Servlet configuration passed in by the servlet conatiner
     */
    @Override
    public void init(ServletConfig servletConfig) {}



    // --- [doGet] ---------------------------------------------------------------------------------
    
    /**
     * Performs an HTTP GET request
     * @param request The {@link HttpServletRequest} object passed in by the servlet
     * engine representing the client request to be proxied
     * @param response The {@link HttpServletResponse} object by which
     * we can send a proxied response to the client 
     */
    @Override
    public void doGet (HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
    	
        // Create a GET request
    	HttpRequestBase proxyRequest = new HttpGet(getProxyURL(request));

        // Forward the request headers
        HttpGet proxyRequestGet = (HttpGet) setProxyRequestHeaders(request, proxyRequest);

        // Execute the proxy request
        executeProxyRequest(proxyRequestGet, request, response);
    }
    
    

    // --- [doPost] --------------------------------------------------------------------------------
    
    /**
     * Performs an HTTP POST request
     * @param request The {@link HttpServletRequest} object passed
     *                            in by the servlet engine representing the
     *                            client request to be proxied
     * @param response The {@link HttpServletResponse} object by which
     *                             we can send a proxied response to the client 
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {

        // Create a standard POST request
    	HttpRequestBase proxyRequest = new HttpPost(getProxyURL(request));

        // Forward the request headers
        proxyRequest = setProxyRequestHeaders(request, proxyRequest);

        HttpPost proxyRequestPost = (HttpPost) proxyRequest;
        
        // Check if this is a mulitpart (file upload) POST
        if (ServletFileUpload.isMultipartContent(request)) {
        	proxyRequestPost = handleMultipartPost(proxyRequestPost, request);
        } else {
        	proxyRequestPost = handleStandardPost(proxyRequestPost, request);
        }

        // Execute the proxy request
        executeProxyRequest(proxyRequestPost, request, response);
    }
    
    

    // --- [handleMultipartPost] -------------------------------------------------------------------
    
    /**
     * Sets up the given {@link PostMethod} to send the same multipart POST
     * data as was sent in the given {@link HttpServletRequest}
     * @param proxyRequest The {@link PostMethod} that we are
     * configuring to send a multipart POST request
     * @param request The {@link HttpServletRequest} that contains
     * the mutlipart POST data to be sent via the {@link PostMethod}
     * @return 
     */

    private HttpPost handleMultipartPost(HttpPost proxyRequest, HttpServletRequest request) throws ServletException {


    	//HttpResponse httpResponse = httpClient.execute(proxyRequest);
    	
        // Create a factory for disk-based file items
        DiskFileItemFactory diskFileItemFactory = new DiskFileItemFactory();

        // Set factory constraints
        diskFileItemFactory.setSizeThreshold(maxFileUploadSize);
        diskFileItemFactory.setRepository(UPLOAD_TEMP_DIRECTORY);

        // Create a new file upload handler
        ServletFileUpload servletFileUpload = new ServletFileUpload(diskFileItemFactory);

        MultipartEntityBuilder multipartRequestEntity = MultipartEntityBuilder.create();
        
        // Parse the request
        try {
            // Get the multipart items as a list
            List<FileItem> listFileItems = servletFileUpload.parseRequest(request);

            

            // Iterate the multipart items list
            for (FileItem fileItemCurrent : listFileItems) {
                // If the current item is a form field, then create a string part
                if (fileItemCurrent.isFormField()) {
                	
                	multipartRequestEntity.addTextBody(fileItemCurrent.getFieldName(), fileItemCurrent.getString());
                	
                   
                } else {
                    // The item is a file upload, so we create a FilePart
                	multipartRequestEntity.addBinaryBody(fileItemCurrent.getFieldName(), fileItemCurrent.get());

                }
            }

            HttpEntity entity = multipartRequestEntity.build();
            
            proxyRequest.setEntity(entity);
            

            // The current content-type header (received from the client) IS of
            // type "multipart/form-data", but the content-type header also
            // contains the chunk boundary string of the chunks. Currently, this
            // header is using the boundary of the client request, since we
            // blindly copied all headers from the client request to the proxy
            // request. However, we are creating a new request with a new chunk
            // boundary string, so it is necessary that we re-set the
            // content-type string to reflect the new chunk boundary string
            //httpResponse.setHeader(CONTENT_TYPE_HEADER_NAME, entity.getContentType().getValue());
            proxyRequest.setHeader(CONTENT_TYPE_HEADER_NAME, entity.getContentType().getValue());
            
        } catch (FileUploadException fileUploadException) {
            throw new ServletException(fileUploadException);
        }
        
        return proxyRequest;
    }



    // --- [handleStandardPost] --------------------------------------------------------------------

    /**
     * Sets up the given {@link PostMethod} to send the same standard POST
     * data as was sent in the given {@link HttpServletRequest}
     * @param proxyRequest The {@link PostMethod} that we are
     *                                configuring to send a standard POST request
     * @param request The {@link HttpServletRequest} that contains
     *                            the POST data to be sent via the {@link PostMethod}
     * @return 
     */    
    private HttpPost handleStandardPost(HttpPost proxyRequest, HttpServletRequest request) {

        // Get the client POST data as a Map
        Map<String, String[]> mapPostParameters = request.getParameterMap();
       
        // Create a List to hold the NameValuePairs to be passed to the PostMethod
        List<NameValuePair> listNameValuePairs = new ArrayList<NameValuePair>();

        // Iterate the parameter names
        for (String stringParameterName : mapPostParameters.keySet()) {
            // Iterate the values for each parameter name
            String[] stringArrayParameterValues = mapPostParameters.get(stringParameterName);
            for (String stringParamterValue : stringArrayParameterValues) {
                // Create a NameValuePair and store in list
                NameValuePair nameValuePair = new BasicNameValuePair(stringParameterName, stringParamterValue);
                listNameValuePairs.add(nameValuePair);
            }
        }
        
        // Set the proxy request POST data 
        try {
			proxyRequest.setEntity(new UrlEncodedFormEntity(listNameValuePairs));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
        
        return proxyRequest;
    }
    
    
    // --- [executeProxyRequest] -------------------------------------------------------------------
    
    /**
     * Executes the {@link HttpMethod} passed in and sends the proxy response
     * back to the client via the given {@link HttpServletResponse}
     * @param proxyRequest An object representing the proxy request to be made
     * @param response An object by which we can send the proxied
     * response back to the client
     * @throws IOException Can be thrown by the {@link HttpClient}.executeMethod
     * @throws ServletException Can be thrown to indicate that another error has occurred
     */
    private void executeProxyRequest(HttpRequestBase proxyRequest, HttpServletRequest request,   HttpServletResponse response)  throws IOException, ServletException {
    	
    	
    	//proxyRequest.

        // Create a default HttpClient

    	HttpClientBuilder httpPre = HttpClientBuilder.create();
    	
//    	httpPre.setRedirectStrategy(new RedirectStrategy() {
//			
//			@Override
//			public boolean isRedirected(HttpRequest arg0, HttpResponse arg1,
//					HttpContext arg2) throws ProtocolException {
//				// TODO Auto-generated method stub
//				return false;
//			}
//			
//			@Override
//			public HttpUriRequest getRedirect(HttpRequest arg0, HttpResponse arg1,
//					HttpContext arg2) throws ProtocolException {
//				// TODO Auto-generated method stub
//				return null;
//			}
//		});
    	
    	HttpClient httpClient = httpPre.build();
    	

    	HttpHost targetHost = new HttpHost(proxyRequest.getFirstHeader(HOST_HEADER_NAME).getValue() , proxyRequest.getURI().getPort());
    	
    	HttpResponse httpResponse = httpClient.execute(targetHost, proxyRequest);
    	
    	int proxyResponseCode = httpResponse.getStatusLine().getStatusCode();
    	
    	
    	

        // Check if the proxy response is a redirect
        // The following code is adapted from org.tigris.noodle.filters.CheckForRedirect
        // Hooray for open source software
        if (proxyResponseCode >= HttpServletResponse.SC_MULTIPLE_CHOICES /* 300 */
                && proxyResponseCode < HttpServletResponse.SC_NOT_MODIFIED /* 304 */) {

            String statusCode = Integer.toString(proxyResponseCode);
            String location   = httpResponse.getFirstHeader(LOCATION_HEADER).getValue();

            if (location == null) {
                throw new ServletException("Recieved status code: " + statusCode 
                        + " but no " +  LOCATION_HEADER + " header was found in the response");
            }

            // Modify the redirect to go to this proxy servlet rather that the proxied host
            String myHostName = request.getServerName();

            if(request.getServerPort() != 80) {
                myHostName += ":" + request.getServerPort();
            }

            myHostName += request.getContextPath();
            response.sendRedirect(location.replace(getProxyHostAndPort(), myHostName));
            return;
        } else if(proxyResponseCode == HttpServletResponse.SC_NOT_MODIFIED) {
            // 304 needs special handling.  See:
            // http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
            // We get a 304 whenever passed an 'If-Modified-Since'
            // header and the data on disk has not changed; server
            // responds w/ a 304 saying I'm not going to send the
            // body because the file has not changed.
            response.setIntHeader(CONTENT_LENGTH_HEADER_NAME, 0);
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        
        // Pass the response code back to the client
        response.setStatus(proxyResponseCode);

        // Pass response headers back to the client
        Header[] responseHeaders = httpResponse.getAllHeaders();

        for (Header header : responseHeaders) {
                response.setHeader(header.getName(), header.getValue());
        }
        
        // Send the content to the client
        InputStream proxyResponse               = httpResponse.getEntity().getContent();;     
        BufferedInputStream bufferedInputStream = new BufferedInputStream(proxyResponse);
        OutputStream        clientResponse      = response.getOutputStream();

        int intNextByte;
        while ((intNextByte = bufferedInputStream.read() ) != -1 ) {
            clientResponse.write(intNextByte);
        }
        return;
        
    }
    
    

    // --- [getServletInfo] ------------------------------------------------------------------------    
    
    @Override
    public String getServletInfo() {

        return "Http Proxy Servlet";
    }


    
    // --- [setProxyRequestHeaders] ----------------------------------------------------------------

    /**
     * Retreives all of the headers from the servlet request and sets them on
     * the proxy request
     * 
     * @param httpServletRequest The request object representing the client's
     * request to the servlet engine
     * @param proxyRequest The request that we are about to send to
     * the proxy host
     * @return 
     */
    private HttpRequestBase setProxyRequestHeaders(HttpServletRequest httpServletRequest, HttpRequestBase proxyRequest) {

        // Get an Enumeration of all of the header names sent by the client
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();

        while(headerNames.hasMoreElements()) {

            String headerName = headerNames.nextElement();

            if (headerName.equalsIgnoreCase(CONTENT_LENGTH_HEADER_NAME))
                continue;
            // As per the Java Servlet API 2.5 documentation:
            //        Some headers, such as Accept-Language can be sent by clients
            //        as several headers each with a different value rather than
            //        sending the header as a comma separated list.
            // Thus, we get an Enumeration of the header values sent by the client

            Enumeration<String> headerValues = httpServletRequest.getHeaders(headerName);

            while (headerValues.hasMoreElements()) {
                String headerValue = headerValues.nextElement();

                // In case the proxy host is running multiple virtual servers,
                // rewrite the Host header to ensure that we get content from
                // the correct virtual server

//                if (headerName.equalsIgnoreCase(HOST_HEADER_NAME)){
//                	System.out.println(">>>>>>>>>>>> "+headerValue);
//                    headerValue = getProxyHostAndPort();
//                }

                // Set the same header on the proxy request
                
                
                proxyRequest.addHeader(headerName, headerValue);

            }
        }
        return proxyRequest;
    }
    
    

    // --- [getProxyURL] ---------------------------------------------------------------------------
    
    private String getProxyURL(HttpServletRequest httpServletRequest) {
    	
    	
    	System.out.println("3: "+httpServletRequest.getRequestURL().toString());
    	System.out.println("3: "+httpServletRequest.getRequestURI());

        // Set the protocol to HTTP
        String stringProxyURL = protocol + "://" + getProxyHostAndPort();
        
       System.out.println("2: "+stringProxyURL);



        // Handle the path given to the servlet
        stringProxyURL += httpServletRequest.getPathInfo();

        // Handle the query string
        if(httpServletRequest.getQueryString() != null) {
            stringProxyURL += "?" + httpServletRequest.getQueryString();
        }

        System.out.println("1: "+stringProxyURL);
        
        return stringProxyURL;
    }
    


    // --- [getProxyHostAndPort] -------------------------------------------------------------------
    
    private String getProxyHostAndPort() {

        if(proxyPort == 80) {
            return proxyHost;
        } else {
            return proxyHost + ":" + proxyPort;
        }
    }

}
