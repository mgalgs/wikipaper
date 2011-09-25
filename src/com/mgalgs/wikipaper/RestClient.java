package com.mgalgs.wikipaper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;


public class RestClient {

	private static final int CONNECTION_TIMEOUT = 5000; // 5 seconds
	private static final int SO_TIMEOUT = 10000; // 10 seconds


	private static String convertStreamToString(InputStream is) {
		/*
		 * To convert the InputStream to String we use the
		 * BufferedReader.readLine() method. We iterate until the BufferedReader
		 * return null which means there's no more data to read. Each line will
		 * appended to a StringBuilder and returned as String.
		 */
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();

		String line = null;
		try {
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				is.close();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
		return sb.toString();
	}


	public static JSONObject connect(String url) {
		JSONObject json = null;
		HttpParams httpParameters = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(httpParameters, CONNECTION_TIMEOUT);
		HttpConnectionParams.setSoTimeout(httpParameters, SO_TIMEOUT);
		HttpClient httpclient = new DefaultHttpClient(httpParameters);
		httpclient.getParams().setParameter(CoreProtocolPNames.USER_AGENT, "RestClient for Android by Mitchel Humpherys <mitch.special@gmail.com>");

		// Prepare a request object
		HttpGet httpget = new HttpGet(url);

		// Execute the request
		HttpResponse response;
		String result = "bogus";
		try {
			Log.d(WikiPaper.WP_LOGTAG, "Downloading " + url);
			response = httpclient.execute(httpget);

			// Get hold of the response entity
			HttpEntity entity = response.getEntity();
			// If the response does not enclose an entity, there is no need
			// to worry about connection release

			if (entity != null) {

				// A Simple JSON Response Read
				InputStream instream = entity.getContent();
				result = convertStreamToString(instream);
				if (result == null) {
					Log.e(WikiPaper.WP_LOGTAG, "Error while converting stream to string in json stuff!");
					return null;
				}

				Log.d(WikiPaper.WP_LOGTAG, "Got download result: " + result);
				// A Simple JSONObject Creation
				json = new JSONObject(result);

				// A Simple JSONObject Parsing
//				JSONArray nameArray = json.names();
//				JSONArray valArray = json.toJSONArray(nameArray);

				
				// A Simple JSONObject Value Pushing
//				json.put("sample key", "sample value");

				// Closing the input stream will trigger connection release
				instream.close();
			}

		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			Log.i("RestClient", "Got an exception while trying to convert " + result);
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return json;
	}
}
