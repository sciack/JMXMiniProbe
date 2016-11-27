/*
 * Copyright (c) 2014, Paessler AG <support@paessler.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.paessler.prtg.api;

import com.paessler.prtg.jmx.Logger;
import com.paessler.prtg.jmx.channels.Channel;
import com.paessler.prtg.jmx.channels.LongChannel;
import com.paessler.prtg.jmx.sensors.http.HTTPEntry;
import com.paessler.prtg.util.TimingUtility;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.SocketTimeoutException;

public class NetworkWrapper {

    public static CloseableHttpClient getClient( int timeout) {
        CloseableHttpClient httpClient = HttpClients
                .custom()
                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .build();
        return httpClient;
    }

    public static void post(String url, String Data) throws IOException {
        post(url, Data, 5000);
    }

    public static void post(String url, String data, int timeout) throws IOException {
        try(CloseableHttpClient httpClient = getClient(timeout)) {
            Logger.log("Uploading " + data + " to " + url);
            HttpPost post = new HttpPost(url);
            post.setEntity(new StringEntity(data));
            post.setHeader("Accept", "application/json");
            post.setHeader("Content-type", "application/json");
            HttpResponse response = httpClient.execute(post);
            if (response == null)
                throw new InterruptedIOException("The network is not working properly");

            StatusLine statusLine = response.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if (statusCode >= 200 && statusCode <= 299) {
                // Great!
            } else {
                String reason = responseToString(response);
                if (reason == null || reason.length() == 0)
                    throw new HttpResponseException(statusCode, statusLine.getReasonPhrase());
                else
                    throw new HttpResponseException(statusCode, reason);
            }
        }
    }

    public static String fetch( String url) throws IOException {
        return fetch( url, 5000);
    }

    public static String fetch(String url, int timeout) throws IOException {
        System.out.println("Fetching " + url);
        try(CloseableHttpClient httpClient = getClient(timeout)) {
            HttpGet get = new HttpGet(url);
            HttpResponse response = null;
            response = httpClient.execute(get);
            if (response == null)
                throw new InterruptedIOException("The network is not working properly");

            StatusLine statusLine = response.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if (statusCode >= 200 && statusCode <= 299) {
                return responseToString(response);
            } else {
                String reason = responseToString(response);
                if (reason == null || reason.length() == 0)
                    throw new HttpResponseException(statusCode, statusLine.getReasonPhrase());
                else
                    throw new HttpResponseException(statusCode, reason);
            }
        }
    }

    public static String responseToString(HttpResponse response) throws IOException {
        StringBuilder builder = new StringBuilder();
        HttpEntity responseEntity = response.getEntity();
        InputStream content = responseEntity.getContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(content));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }

        responseEntity.consumeContent();
        return builder.toString();
    }


	//----------------------------------------------------------------------
	protected Channel issueHTTPRequest(HTTPEntry entry) throws Exception{
		LongChannel retVal = new LongChannel(entry.getDescription(), Channel.Unit.TIME_RESPONSE, 0, Channel.Mode.INTEGER);
		TimingUtility sensorcreationtimer = new TimingUtility();
		HttpUriRequest   req = null;
		CloseableHttpClient cli = null;
		CloseableHttpResponse ret = null;
		try{
	        HttpClientContext localContext = HttpClientContext.create();
	        localContext.setAuthCache(entry.getAuthCache());
	 
			req = entry.getHttpRequest();
			cli = entry.getCloseableHttpClient();
//			ret = cli.execute(req, localContext);
			ret = cli.execute(req);
			retVal.setValue(sensorcreationtimer.getElapsed());
		} catch(SocketTimeoutException e){
			retVal.setValue(sensorcreationtimer.getElapsed());
			retVal.setError("Socket");
			retVal.setWarning(1);
			retVal.setMessage(e.getMessage());
		} catch(SSLException e){
			retVal.setValue(sensorcreationtimer.getElapsed());
			retVal.setError("SSLException");
			retVal.setWarning(1);
			retVal.setMessage(e.getMessage());
		} catch(Exception e){
			retVal.setValue(sensorcreationtimer.getElapsed());
			retVal.setError("Exception");
			retVal.setWarning(1);
			retVal.setMessage(e.getMessage());
		} finally
		{
			if(ret != null){
				ret.close();
			}
			cli.close();
		}
		return retVal;
	}
    
}
