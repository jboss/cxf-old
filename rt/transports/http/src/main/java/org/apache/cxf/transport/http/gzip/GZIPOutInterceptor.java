/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.transport.http.gzip;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.HttpHeaderHelper;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.MessageSenderInterceptor;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/**
 * CXF interceptor that compresses outgoing messages using gzip and sets the
 * HTTP Content-Encoding header appropriately. An instance of this class should
 * be added as an out interceptor on clients that need to talk to a service that
 * accepts gzip-encoded requests or on a service that wants to be able to return
 * compressed responses. In server mode, the interceptor only compresses
 * responses if the client indicated (via an Accept-Encoding header on the
 * request) that it can understand them. To handle gzip-encoded input messages,
 * see {@link GZIPInInterceptor}. This interceptor supports a compression
 * {@link #threshold} (default 1kB) - messages smaller than this threshold will
 * not be compressed. To force compression of all messages, set the threshold to
 * 0. This class was originally based on one of the CXF samples
 * (configuration_interceptor).
 * 
 * @author Ian Roberts (i.roberts@dcs.shef.ac.uk)
 */
public class GZIPOutInterceptor extends AbstractPhaseInterceptor<Message> {

    /**
     * Enum giving the possible values for whether we should gzip a particular
     * message.
     */
    public static enum UseGzip {
        NO, YES, FORCE
    }

    /**
     * Key under which we store the original output stream on the message, for
     * use by the ending interceptor.
     */
    public static final String ORIGINAL_OUTPUT_STREAM_KEY = GZIPOutInterceptor.class.getName()
                                                            + ".originalOutputStream";

    /**
     * Key under which we store an indication of whether compression is
     * permitted or required, for use by the ending interceptor.
     */
    public static final String USE_GZIP_KEY = GZIPOutInterceptor.class.getName() + ".useGzip";

    /**
     * Key under which we store the name which should be used for the
     * content-encoding of the outgoing message. Typically "gzip" but may be
     * "x-gzip" if we are processing a response message and this is the name
     * given by the client in Accept-Encoding.
     */
    public static final String GZIP_ENCODING_KEY = GZIPOutInterceptor.class.getName() + ".gzipEncoding";

    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(GZIPOutInterceptor.class);
    private static final Logger LOG = LogUtils.getL7dLogger(GZIPOutInterceptor.class);

    /**
     * Ending interceptor that handles the compression process.
     */
    private GZIPOutEndingInterceptor ending = new GZIPOutEndingInterceptor();

    /**
     * Compression threshold in bytes - messages smaller than this will not be
     * compressed.
     */
    private int threshold = 1024;

    public GZIPOutInterceptor() {
        super(Phase.PREPARE_SEND);
        addAfter(MessageSenderInterceptor.class.getName());
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public int getThreshold() {
        return threshold;
    }

    public void handleMessage(Message message) throws Fault {
        UseGzip use = gzipPermitted(message);
        if (use != UseGzip.NO) {
            // remember the original output stream, we will write compressed
            // data to this later
            OutputStream os = message.getContent(OutputStream.class);
            message.put(ORIGINAL_OUTPUT_STREAM_KEY, os);

            message.put(USE_GZIP_KEY, use);

            // new stream to cache the message
            CachedOutputStream cs = new CachedOutputStream();
            message.setContent(OutputStream.class, cs);

            // add the ending interceptor that does the work
            message.getInterceptorChain().add(ending);
        }
    }

    /**
     * Checks whether we can, cannot or must use gzip compression on this output
     * message. Gzip is always permitted if the message is a client request. If
     * the message is a server response we check the Accept-Encoding header of
     * the corresponding request message - with no Accept-Encoding we assume
     * that gzip is not permitted. For the full gory details, see <a
     * href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.3">section
     * 14.3 of RFC 2616</a> (HTTP 1.1).
     * 
     * @param message the outgoing message.
     * @return whether to attempt gzip compression for this message.
     * @throws Fault if the Accept-Encoding header does not allow any encoding
     *                 that we can support (identity, gzip or x-gzip).
     */
    private UseGzip gzipPermitted(Message message) throws Fault {
        UseGzip permitted = UseGzip.NO;
        if (Boolean.TRUE.equals(message.get(Message.REQUESTOR_ROLE))) {
            LOG.fine("Requestor role, so gzip enabled");
            permitted = UseGzip.YES;
            message.put(GZIP_ENCODING_KEY, "gzip");
        } else {
            LOG.fine("Response role, checking accept-encoding");
            Exchange exchange = message.getExchange();
            Message request = exchange.getInMessage();
            Map<String, List<String>> requestHeaders = CastUtils.cast((Map<?, ?>)request
                .get(Message.PROTOCOL_HEADERS));
            if (requestHeaders != null) {
                List<String> acceptEncodingHeader = CastUtils.cast(HttpHeaderHelper
                    .getHeader(requestHeaders, HttpHeaderHelper.ACCEPT_ENCODING));
                if (acceptEncodingHeader != null) {
                    if (LOG.isLoggable(Level.FINE)) {
                        LOG.fine("Accept-Encoding header: " + acceptEncodingHeader);
                    }
                    // Accept-Encoding is a comma separated list of entries, so
                    // we split it into its component parts and build two
                    // lists, one with all the "q=0" encodings and the other
                    // with the rest (no q, or q=<non-zero>).
                    List<String> zeros = new ArrayList<String>(3);
                    List<String> nonZeros = new ArrayList<String>(3);

                    // regular expression that matches any encoding with a
                    // q-value of 0 (or 0.0, 0.00, etc.).
                    Pattern zeroQ = Pattern.compile(";\\s*q=0(?:\\.0+)?$");
                    for (String headerLine : acceptEncodingHeader) {
                        String[] encodings = headerLine.trim().split("[,\\s]*,\\s*");

                        for (String enc : encodings) {
                            Matcher m = zeroQ.matcher(enc);
                            if (m.find()) {
                                zeros.add(enc.substring(0, m.start()));
                            } else if (enc.indexOf(';') >= 0) {
                                nonZeros.add(enc.substring(0, enc.indexOf(';')));
                            } else {
                                nonZeros.add(enc);
                            }
                        }
                    }

                    // identity encoding is permitted if (a) it is not
                    // specifically disabled by an identity;q=0 and (b) if
                    // there is a *;q=0 then there is also an explicit
                    // identity[;q=<non-zero>]
                    //
                    // [x-]gzip is permitted if (a) there is an explicit
                    // [x-]gzip[;q=<non-zero>], or (b) there is a
                    // *[;q=<non-zero>] and no [x-]gzip;q=0 to disable it.
                    boolean identityEnabled = !zeros.contains("identity")
                                              && (!zeros.contains("*") || nonZeros.contains("identity"));
                    boolean gzipEnabled = nonZeros.contains("gzip")
                                          || (nonZeros.contains("*") && !zeros.contains("gzip"));
                    boolean xGzipEnabled = nonZeros.contains("x-gzip")
                                           || (nonZeros.contains("*") && !zeros.contains("x-gzip"));

                    if (identityEnabled && !gzipEnabled && !xGzipEnabled) {
                        permitted = UseGzip.NO;
                    } else if (identityEnabled && gzipEnabled) {
                        permitted = UseGzip.YES;
                        message.put(GZIP_ENCODING_KEY, "gzip");
                    } else if (identityEnabled && xGzipEnabled) {
                        permitted = UseGzip.YES;
                        message.put(GZIP_ENCODING_KEY, "x-gzip");
                    } else if (!identityEnabled && gzipEnabled) {
                        permitted = UseGzip.FORCE;
                        message.put(GZIP_ENCODING_KEY, "gzip");
                    } else if (!identityEnabled && xGzipEnabled) {
                        permitted = UseGzip.FORCE;
                        message.put(GZIP_ENCODING_KEY, "x-gzip");
                    } else {
                        throw new Fault(new org.apache.cxf.common.i18n.Message("NO_SUPPORTED_ENCODING",
                                                                               BUNDLE));
                    }
                } else {
                    LOG.fine("No accept-encoding header");
                }
            }
        }

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("gzip permitted: " + permitted);
        }
        return permitted;
    }

    /**
     * Ending interceptor to actually do the compression.
     */
    public class GZIPOutEndingInterceptor extends AbstractPhaseInterceptor<Message> {

        public GZIPOutEndingInterceptor() {
            super(Phase.PREPARE_SEND_ENDING);
            addBefore(MessageSenderInterceptor.MessageSenderEndingInterceptor.class.getName());
        }

        /**
         * Copies the message content to the real output stream, compressing it
         * if we have to or if the message is larger than the threshold.
         */
        public void handleMessage(Message message) throws Fault {
            try {
                CachedOutputStream cs = (CachedOutputStream)message.getContent(OutputStream.class);
                cs.flush();
                OutputStream originalOutput = (OutputStream)message.get(ORIGINAL_OUTPUT_STREAM_KEY);
                if (UseGzip.FORCE == message.get(USE_GZIP_KEY) || cs.size() > threshold) {
                    LOG.fine("Compressing message.");
                    // Set the Content-Encoding HTTP header
                    addHeader(message, "Content-Encoding", (String)message.get(GZIP_ENCODING_KEY));
                    // if this is a response message, add the Vary header
                    if (!Boolean.TRUE.equals(message.get(Message.REQUESTOR_ROLE))) {
                        addHeader(message, "Vary", "Accept-Encoding");
                    }

                    // gzip the result
                    GZIPOutputStream zipOutput = new GZIPOutputStream(originalOutput);
                    cs.writeCacheTo(zipOutput);
                    zipOutput.finish();
                } else {
                    LOG.fine("Message is smaller than compression threshold, not compressing.");
                    cs.writeCacheTo(originalOutput);
                }

                cs.close();
                originalOutput.flush();

                message.setContent(OutputStream.class, originalOutput);
            } catch (IOException ex) {
                throw new Fault(new org.apache.cxf.common.i18n.Message("COULD_NOT_ZIP", BUNDLE), ex);
            }
        }

        /**
         * Adds a value to a header. If the given header name is not currently
         * set in the message, an entry is created with the given single value.
         * If the header is already set, the value is appended to the first
         * element of the list, following a comma.
         * 
         * @param message the message
         * @param name the header to set
         * @param value the value to add
         */
        private void addHeader(Message message, String name, String value) {
            Map<String, List<String>> protocolHeaders = CastUtils.cast((Map<?, ?>)message
                .get(Message.PROTOCOL_HEADERS));
            if (protocolHeaders == null) {
                protocolHeaders = new HashMap<String, List<String>>();
                message.put(Message.PROTOCOL_HEADERS, protocolHeaders);
            }
            List<String> header = CastUtils.cast((List<?>)protocolHeaders.get(name));
            if (header == null) {
                header = new ArrayList<String>();
                protocolHeaders.put(name, header);
            }
            if (header.size() == 0) {
                header.add(value);
            } else {
                header.set(0, header.get(0) + "," + value);
            }
        }
    }
}
