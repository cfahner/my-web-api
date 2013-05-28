/*
 Copyright 2013 FahnerIT

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package it.fahner.mywapi;

import it.fahner.mywapi.http.HttpRequest;
import it.fahner.mywapi.http.HttpRequestTimeoutException;
import it.fahner.mywapi.http.HttpResponse;
import it.fahner.mywapi.http.types.HttpParamList;
import it.fahner.mywapi.myutil.MyOpenRequestsTracker;
import it.fahner.mywapi.myutil.MyWebApiListenerCollection;
import it.fahner.mywapi.myutil.MyWebCache;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * The main entry point for the MyWebApi library. All requests, threads, caches and listeners
 * are managed here.
 * <p>Most basic setup:</p>
 * <p><code>
 * MyWebApi apiInstance = new MyWebApi("http://hostname.com/api/location/");
 * apiInstance.startListening(this);
 * </code></p>
 * <p>To start a new request, pass an implementation of {@link MyRequest} to
 * {@link #startRequest(MyRequest)}. It is recommended to extend {@link MyBaseRequest} for your own requests
 * instead of using the actual interface, since that will reduce the chances of your code breaking due to an interface
 * change.</p>
 * <p>When any request is resolved, all listeners are notified using their {@link MyWebApiListener#onRequestResolved(MyRequest)}
 * method.</p>
 * <p>Caching is enabled by default, but can only work if {@link MyRequest#getCacheTime()} returns
 * a value greater than zero AND {@link MyRequest#getContentName()} returns a non-<code>null</code> value.</p>
 * <p>If a completed request causes some content to become invalid (as a result of the operation of that request),
 * use {@link #invalidateContent(String)} to invalidate the cache for that type of content. This will also notify
 * all other listeners through their {@link MyWebApiListener#onContentInvalidated(String)} method.</p>
 * <p>Persisting the cache through multiple sessions requires you to serialize the returned value
 * of {@link #getCache()} and store it to disk. Later re-instantiate the cache and apply it to the API using
 * {@link #setCache(MyWebCache)}. A better API will be provided in a future release.</p>
 * @since MyWebApi 1.0
 * @author C. Fahner <info@fahnerit.com>
 */
public final class MyWebApi {
	
	/**
	 * Represents the current version of this library.
	 * @since MyWebApi 1.0
	 */
	public static final String VERSION = "1.0";
	
	/**
	 * The request timeout in milliseconds used when no timeout has been set specifically.
	 * <p>Some platforms may enforce their own timeout value.</p>
	 * @since MyWebApi 1.0
	 */
	public static final int DEFAULT_TIMEOUT = 15000;
	
	/** Contains the base URL of this MyWebApi. */
	private String baseUrl;
	
	/** Contains all URL parameters that need to be included in every request. */
	private HttpParamList persistentUrlParams;
	
	/** Contains the time in milliseconds before a single request is cancelled. */
	private int timeoutMillis;
	
	/** Flag indicating if this class should use a cache. Defaults to <code>true</code>. */
	private boolean useCache;
	
	/** Stores all registered {@link MyWebApiListener}s. */
	private MyWebApiListenerCollection listeners;
	
	/** Keeps track of all currently opened requests. */
	private MyOpenRequestsTracker openRequests;
	
	/** Keeps track of all cached content. */
	private MyWebCache cache;
	
	/**
	 * Creates a new access point to a web-based API.
	 * <p>The cache will start enabled.</p>
	 * @since MyWebApi 1.0
	 * @param configs A configurations object for this API
	 */
	public MyWebApi(String baseUrl) {
		this.baseUrl = baseUrl;
		this.persistentUrlParams = new HttpParamList();
		this.timeoutMillis = DEFAULT_TIMEOUT;
		this.useCache = true;
		this.listeners = new MyWebApiListenerCollection();
		this.openRequests = new MyOpenRequestsTracker();
		this.cache = new MyWebCache();
	}
	
	/**
	 * Converts a {@link MyRequest} into an {@link HttpRequest} that we can get the response for.
	 * @debug This function is made public for debugging purposes only, do not use in production!
	 * @since MyWebApi 1.0
	 * @param myReq The request to convert
	 * @return The HttpRequest that represents the resource the MyRequest wants to retrieve
	 */
	public HttpRequest convertToHttpRequest(MyRequest myReq) {
		String urlToUse = baseUrl;
		String query = myReq.getUrlParameters().merge(persistentUrlParams).toUrlQuery();
		try {
			// Try to use the base URL + the path specified by the request + the query
			urlToUse = myReq.getPath() != null
					? new URL(urlToUse + myReq.getPath() + query).toExternalForm()
					: new URL(urlToUse + query).toExternalForm();
		} catch (MalformedURLException e) {
			// If base+path+query is malformed, just use base+query, which (if malformed) will fail
			// automatically when it is passed to the HttpRequest
			System.err.println("MyWebApi: malformed full URL, reverting to base URL");
			urlToUse += query;
		}
		HttpRequest out = myReq.getRequestMethod() != null
				? new HttpRequest(urlToUse, myReq.getRequestMethod())
				: new HttpRequest(urlToUse);
		if (myReq.getBody() != null) { out.setBody(myReq.getBody()); }
		return out;
	}
	
	/**
	 * Registers a callback to be invoked when any {@link MyRequest}s are resolved.
	 * @since MyWebApi 1.0
	 * @param listener The callback to register
	 */
	public void startListening(MyWebApiListener listener) {
		listeners.put(listener);
	}
	
	/**
	 * Starts a single request. Invokes the callback of every listener when the request has finished.
	 * <p>If MyWebApi is still waiting for another request that points to the same resource (to the same URL
	 * with the same parameters), no new request will be started.</p>
	 * <p>If an response is stored in the cache and has not yet expired, that response is returned
	 * instead of sending a new request (unless the cache is disabled).</p>
	 * @since MyWebApi 1.0
	 * @param request An implementation of MyRequest that needs to be resolved
	 */
	public void startRequest(final MyRequest request) {
		final HttpRequest http = convertToHttpRequest(request);
		if (openRequests.isOpen(http)) { return; }
		
		// Check if the cache has a valid response ready now (if it is used)
		if (useCache && request.getContentName() != null && cache.hasResponse(request.getContentName(), http)) {
			request.complete(cache.getResponse(request.getContentName(), http));
			listeners.invokeAllResolved(request);
			return;
		}
		
		// Try to get the response (on a separate thread, so we don't block the main thread)
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				openRequests.storeRequest(http);
				try {
					HttpResponse response = http.getResponse(timeoutMillis);
					request.complete(response);
					long cacheTime = request.getCacheTime();
					if (useCache && cacheTime > 0) { cache.add(request.getContentName(), response, cacheTime); }
				} catch (HttpRequestTimeoutException e) { request.fail(); }
				openRequests.removeRequest(http);
				listeners.invokeAllResolved(request);
			}
			
		}).start();
	}
	
	/**
	 * Invalidates all cached responses that are stored under the given content name.
	 * <p>Also notifies all listeners that content with the given name has been invalidated.</p>
	 * @since MyWebApi 1.0
	 * @param contentName The content name to invalidate
	 */
	public void invalidateContent(String contentName) {
		cache.removeAll(contentName);
		listeners.invokeAllContentInvalidated(contentName);
	}
	
	/**
	 * Returns the entire caching structure.
	 * @deprecated Temporary function that allows persisting of the cache, will be
	 *  replaced by better API in future versions
	 * @since MyWebApi 1.0
	 * @return The entire cache
	 */
	public MyWebCache getCache() {
		return this.cache;
	}
	
	/**
	 * Sets the cache of this web API.
	 * @deprecated Temporary function that allows setting the cache to a persisted version,
	 *  will be replaced by better API in future versions
	 * @since MyWebApi 1.0
	 * @param cache The cache to use
	 */
	public void setCache(MyWebCache cache) {
		this.cache = cache;
	}
	
	/**
	 * Sets a URL parameter that is included with every request.
	 * @since MyWebApi 1.0
	 * @param name The name of the parameter to set
	 * @param value The value of the parameter to set
	 */
	public void setPersistentUrlParameter(String name, String value) {
		this.persistentUrlParams.set(name, value);
	}
	
	/**
	 * Removes a parameter from the list of persistent URL parameters. This parameter
	 * will no longer be included with every request.
	 * @since MyWebApi 1.0
	 * @param name The name of the parameter to remove
	 */
	public void removePersistentUrlParameter(String name) {
		this.persistentUrlParams.remove(name);
	}
	
	/**
	 * Sets the amount of time to wait before a request is cancelled.
	 * @since MyWebApi 1.0
	 * @param milliseconds Amount of time to wait in milliseconds
	 */
	public void setTimeout(int milliseconds) {
		this.timeoutMillis = milliseconds;
	}
	
	/**
	 * Changes the enabled state of the cache.
	 * <p>Clears the cache when it is disabled.</p>
	 * @since MyWebApi 1.0
	 * @param enable <code>true</code> if you want to enable the cache, <code>false</code> to
	 *  disable
	 */
	public void setCacheEnabled(boolean enable) {
		this.useCache = enable;
		if (!useCache) { cache.clear(); }
	}
	
}
