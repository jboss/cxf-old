package org.objectweb.celtix.interceptors;

import java.util.Iterator;
import org.objectweb.celtix.message.Message;

public interface InterceptorChain  {
    
    void add(Interceptor i);
    
    void remove(Interceptor i);
    
    /**
     * Executes the next filter in the chain.
     * @param message
     */
    void doIntercept(Message message);

    Iterator<Interceptor> getIterator();
}
