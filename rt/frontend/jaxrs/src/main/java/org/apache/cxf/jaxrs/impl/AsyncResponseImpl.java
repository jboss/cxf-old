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
package org.apache.cxf.jaxrs.impl;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.TimeoutHandler;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.cxf.continuations.Continuation;
import org.apache.cxf.continuations.ContinuationProvider;
import org.apache.cxf.jaxrs.utils.HttpUtils;
import org.apache.cxf.message.Message;


public class AsyncResponseImpl implements AsyncResponse {
    
    private Continuation cont;
    private long timeout = AsyncResponse.NO_TIMEOUT;
    private Message inMessage;
    private boolean cancelled;
    private boolean done;
    private boolean newTimeoutRequested;
    private boolean resumedByApplication;
    private TimeoutHandler timeoutHandler;
    public AsyncResponseImpl(Message inMessage) {
        ContinuationProvider provider = 
            (ContinuationProvider)inMessage.get(ContinuationProvider.class.getName());
        cont = provider.getContinuation();
        inMessage.put(AsyncResponse.class, this);
        this.inMessage = inMessage;
       
    }
    
    @Override
    public void resume(Object response) throws IllegalStateException {
        doResume(response);
    }

    @Override
    public void resume(Throwable response) throws IllegalStateException {
        doResume(response);
    }
    
    private synchronized void doResume(Object response) throws IllegalStateException {
        checkCancelled();
        checkSuspended();
        inMessage.getExchange().put(AsyncResponse.class, this);
        cont.setObject(response);
        resumedByApplication = true;
        cont.resume();
    }
    
    @Override
    public void cancel() {
        doCancel(null);
    }

    @Override
    public void cancel(int retryAfter) {
        doCancel(Integer.toString(retryAfter));
    }

    @Override
    public void cancel(Date retryAfter) {
        doCancel(HttpUtils.getHttpDateFormat().format(retryAfter));
    }
    
    private synchronized void doCancel(String retryAfterHeader) {
        checkSuspended();
        cancelled = true;
        ResponseBuilder rb = Response.status(503);
        if (retryAfterHeader != null) {
            rb.header(HttpHeaders.RETRY_AFTER, retryAfterHeader);
        }
        doResume(rb.build());
    }

    @Override
    public synchronized boolean isSuspended() {
        return cont.isPending();
    }

    @Override
    public synchronized boolean isCancelled() {
        return cancelled;
    }

    @Override
    public synchronized boolean isDone() {
        return done;
    }

    @Override
    public synchronized void setTimeout(long time, TimeUnit unit) throws IllegalStateException {
        checkCancelled();
        checkSuspended();
        inMessage.getExchange().put(AsyncResponse.class, this);
        timeout = unit.convert(time, TimeUnit.MILLISECONDS);
        newTimeoutRequested = true;
        cont.resume();
    }

    @Override
    public void setTimeoutHandler(TimeoutHandler handler) {
        timeoutHandler = handler;
    }

    @Override
    public boolean register(Class<?> callback) throws NullPointerException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean[] register(Class<?> callback, Class<?>... callbacks) throws NullPointerException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean register(Object callback) throws NullPointerException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean[] register(Object callback, Object... callbacks) throws NullPointerException {
        // TODO Auto-generated method stub
        return null;
    }
    
    private void checkCancelled() {
        if (cancelled) {
            throw new IllegalStateException();
        }
    }
    
    private void checkSuspended() {
        if (!cont.isPending()) {
            throw new IllegalStateException();
        }
    }
    
    // these methods are called by the runtime, not part of AsyncResponse    
    public synchronized void suspend() {
        checkCancelled();
        cont.suspend(timeout);
    }
    
    public synchronized Object getResponseObject() {
        // it may have to be set to true only after a continuation-specific onComplete event
        done = true;
        return cont.getObject();
    }
    
    public synchronized boolean isResumedByApplication() {
        return resumedByApplication;
    }
    
    public synchronized boolean handleTimeout() {
        if (!resumedByApplication) {
            if (newTimeoutRequested) {
                newTimeoutRequested = false;
                suspend();
                return true;
            } else if (timeoutHandler != null) {
                suspend();
                timeoutHandler.handleTimeout(this);
                return true;
            } else {
                done = true;
            }
        }
        return false;
        
    }
}
