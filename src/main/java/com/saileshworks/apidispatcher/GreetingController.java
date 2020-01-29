package com.saileshworks.apidispatcher;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

// https://stackoverflow.com/questions/31452074/how-to-proxy-http-requests-in-spring-mvc
@RestController
public class GreetingController {
    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();
    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    private String getFreeServer() {
        // todo select a free server from a list of servers
        // implement algorithm here to select best server for request.
        // throw error if no server are free
        return "http://localhost:4000";
    }

    public String proxyRequest(HttpServletRequest request, HttpServletResponse response) {
        try {
            HttpUriRequest proxiedRequest = createHttpUriRequest(request);
            HttpResponse proxiedResponse = httpClient.execute(proxiedRequest);
            String responseFromProxy = writeToResponse(proxiedResponse, response);
            return responseFromProxy;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return e.toString();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            return e.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return e.toString();
        }
    }

    private String writeToResponse(HttpResponse proxiedResponse, HttpServletResponse response){
        for(Header header : proxiedResponse.getAllHeaders()){
            if ((! header.getName().equals("Transfer-Encoding")) || (! header.getValue().equals("chunked"))) {
                response.addHeader(header.getName(), header.getValue());
            }
        }
        OutputStream os = null;
        InputStream is = null;
        try {
            is = proxiedResponse.getEntity().getContent();
            os = response.getOutputStream();
            IOUtils.copy(is, os);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return e.toString();
        } finally {
            if (os != null) {
                try {
                    os.close();
                    return os.toString();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return e.toString();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    return e.toString();
                }
            }
        }
        return "stuck waiting at writeToResponse";
    }

    private HttpUriRequest createHttpUriRequest(HttpServletRequest request) throws URISyntaxException {
        System.out.println(request.getRequestURI());
        URI uri = new URI(getFreeServer()+request.getRequestURI()+"?"+request.getQueryString());
//        URI uri = new URI(geoserverConfig.getUrl()+"/wms?"+request.getQueryString());

        RequestBuilder rb = RequestBuilder.create(request.getMethod());
        rb.setUri(uri);

        Enumeration<String> headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()){
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            rb.addHeader(headerName, headerValue);
        }

        HttpUriRequest proxiedRequest = rb.build();
        return proxiedRequest;
    }

    @GetMapping("/greeting")
    public Greeting greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
        return new Greeting(counter.incrementAndGet(), String.format(template, name));
    }
    @GetMapping("/**")
    public String getDispatch(HttpServletRequest request, HttpServletResponse response) {
        return proxyRequest(request, response);
    }

    @PostMapping("/**")
    public String postDispatch(HttpServletRequest request, HttpServletResponse response) {
        return proxyRequest(request, response);
    }
}
