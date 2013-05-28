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

import it.fahner.mywapi.http.types.HttpContentType;
import it.fahner.mywapi.http.types.HttpStatusCode;

/**
 * Represents a simplified HTTP response. Instances of this class are immutable.
 * @since MyWebApi 1.0
 * @author C. Fahner <info@fahnerit.com>
 */
public final class HttpResponse {
	
	/** Contains the HTTP status code of the response. */
	private HttpStatusCode status;
	
	/** Contains the raw response body. */
	private String body;
	
	/** Contains the content type of this response. */
	private HttpContentType contentType;
	
	/** Contains the timestamp after which this document is no longer cacheable. Can be <code>null</code>. */
	private long expires;
	
	/**
	 * Creates a new simple HTTP response representation.
	 * @since MyWebApi 1.0
	 * @param status The status code of this response
	 * @param body The response body
	 * @param ctype The content type of the body
	 * @param enctype The character encoding used
	 * @param expires The timestamp for when this response is supposed to expire (in millis since 1970)
	 */
	public HttpResponse(HttpStatusCode status, String body, HttpContentType contentType, long expires) {
		this.status = status;
		this.body = body;
		this.contentType = contentType;
		this.expires = expires;
	}
	
	public HttpResponse(HttpStatusCode status, String body, HttpContentType contentType) {
		this(status, body, contentType, 0);
	}
	
	public HttpResponse(HttpStatusCode status, String body) {
		this(status, body, new HttpContentType("text/plain"));
	}
	
	public HttpResponse(HttpStatusCode status) {
		this(status, "");
	}
	
	@Override
	public String toString() {
		return "{HttpResponse: '" + status.getCode() + "-" + status.name() + "' }";
	}
	
	/**
	 * Returns the HTTP status code of this response.
	 * @since MyWebApi 1.0
	 * @see HttpStatusCode
	 * @return The HTTP status code
	 */
	public HttpStatusCode getStatus() {
		return status;
	}
	
	/**
	 * Returns the response body.
	 * @since MyWebApi 1.0
	 * @return The response body
	 */
	public String getBody() {
		return body;
	}
	
	/**
	 * Returns the content type of this HTTP response.
	 * @since MyWebApi 1.0
	 * @return The content type of the response body
	 */
	public HttpContentType getContentType() {
		return contentType;
	}
	
	/**
	 * Returns the expiration time of this response. This is the number of milliseconds
	 * since January 1, 1970 GMT.
	 * <p>Defaults to zero if not specified by the server.</p>
	 * @since MyWebApi 1.0
	 * @return The expiration time of the response
	 */
	public long getExpireTime() {
		return expires;
	}
	
	/**
	 * Returns the amount of milliseconds left until this HTTP response expires. This is based on the
	 * <code>'Expires'</code> header returned by the server and the current {@link HttpStatusCode}.
	 * @see HttpStatusCode#isAlwaysCacheable() Cacheable HTTP status codes
	 * @since MyWebApi 1.0
	 * @return The amount of milliseconds left before this response expires based on server information,
	 *  always returns <code>0</code> if the current {@link HttpStatusCode} is not reliably cacheable.
	 */
	public long cacheableMillisLeft() {
		if (!status.isAlwaysCacheable()) { return 0; }
		long timeLeft = expires - System.currentTimeMillis();
		return timeLeft > 0 ? timeLeft : 0;
	}
	
}
