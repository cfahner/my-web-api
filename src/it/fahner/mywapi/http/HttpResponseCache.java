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

package it.fahner.mywapi.http;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A basic cache that links {@link HttpRequest}s to {@link HttpResponse}s and automatically
 * ensures that cached content expires when it needs to.
 * @since MyWebApi 1.0
 * @author C. Fahner <info@fahnerit.com>
 */
public final class HttpResponseCache {
	
	/** Stores all cached objects. */
	private HashMap<String, HttpResponse> cache;
	
	/** Stores the times when content expires (determined by key). */
	private HashMap<String, Long> expireTimes;
	
	/**
	 * Creates a new empty HTTP response caching structure.
	 * @since MyWebApi 1.0
	 */
	public HttpResponseCache() {
		this.cache = new HashMap<String, HttpResponse>();
		this.expireTimes = new HashMap<String, Long>();
	}
	
	/**
	 * Caches an HTTP response for a specific amount of time.
	 * @since MyWebApi 1.0
	 * @param response The response to store in the cache
	 * @param expireAfter The amount of time to store the response (in milliseconds)
	 */
	public synchronized void store(HttpResponse response, long expireAfter) {
		cache.put(response.getOriginRequest().getResourceIdentity(), response);
		expireTimes.put(response.getOriginRequest().getResourceIdentity(), System.currentTimeMillis() + expireAfter);
	}
	
	/**
	 * Checks if a response for the specified request is contained in this cache.
	 * @since MyWebApi 1.0
	 * @param request The request to check a response for
	 * @return <code>true</code> if a response is contained in this cache, <code>false</code> otherwise
	 */
	public synchronized boolean hasResponseFor(HttpRequest request) {
		clean();
		return cache.containsKey(request.getResourceIdentity());
	}
	
	/**
	 * Returns the cached response for the specified request. Returns <code>null</code> if no cached
	 * response is available (anymore).
	 * @since MyWebApi 1.0
	 * @param request The request to get the cached response for
	 * @return The cached {@link HttpResponse} or <code>null</code> when no response has been cached (or has expired)
	 */
	public synchronized HttpResponse getResponseFor(HttpRequest request) {
		clean();
		return cache.get(request.getResourceIdentity());
	}
	
	/**
	 * Returns the amount of HTTP responses currently in the cache.
	 * @since MyWebApi 1.0
	 * @return Amount of cached responses
	 */
	public synchronized int size() {
		return cache.size();
	}
	
	/**
	 * Cleans all cached elements that have expired.
	 */
	private synchronized void clean() {
		if (expireTimes.size() <= 0) { return; } // prevent instantiation of objects below
		ArrayList<String> toClean = new ArrayList<String>();
		for (String key : expireTimes.keySet()) {
			if (expireTimes.get(key).longValue() < System.currentTimeMillis()) { toClean.add(key); }
		}
		for (String removeResId : toClean) {
			cache.remove(removeResId);
			expireTimes.remove(removeResId);
		}
	}
	
}
