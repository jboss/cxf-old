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
package org.apache.cxf.jaxrs.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Priorities;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.WriterInterceptor;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.util.ClassHelper;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.jaxrs.ext.RequestHandler;
import org.apache.cxf.jaxrs.ext.ResponseHandler;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.impl.RequestPreprocessor;
import org.apache.cxf.jaxrs.impl.ResourceInfoImpl;
import org.apache.cxf.jaxrs.impl.WebApplicationExceptionMapper;
import org.apache.cxf.jaxrs.model.BeanParamInfo;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.model.wadl.WadlGenerator;
import org.apache.cxf.jaxrs.utils.AnnotationUtils;
import org.apache.cxf.message.Message;

public final class ServerProviderFactory extends ProviderFactory {
    private static final String SHARED_SERVER_FACTORY = "jaxrs.shared.server.factory";
    private static final Class<?>[] FILTER_INTERCEPTOR_CLASSES = 
        new Class<?>[] {ContainerRequestFilter.class,
                        ContainerResponseFilter.class,
                        ReaderInterceptor.class,
                        WriterInterceptor.class};
    // Server specific providers
    private List<ProviderInfo<ExceptionMapper<?>>> exceptionMappers = 
        new ArrayList<ProviderInfo<ExceptionMapper<?>>>(1);
    
    // RequestHandler & ResponseHandler will have to be deprecated for 2.7.0
    private List<ProviderInfo<RequestHandler>> requestHandlers = 
        new ArrayList<ProviderInfo<RequestHandler>>(1);
    private List<ProviderInfo<ResponseHandler>> responseHandlers = 
        new ArrayList<ProviderInfo<ResponseHandler>>(1);
    
    // ContainerRequestFilter & ContainerResponseFilter are introduced in JAX-RS 2.0
    private List<ProviderInfo<ContainerRequestFilter>> preMatchContainerRequestFilters = 
        new ArrayList<ProviderInfo<ContainerRequestFilter>>(1);
    //TODO: consider using List as a value type for postmatching filters
    private Map<NameKey, ProviderInfo<ContainerRequestFilter>> postMatchContainerRequestFilters = 
        new LinkedHashMap<NameKey, ProviderInfo<ContainerRequestFilter>>();
    private Map<NameKey, ProviderInfo<ContainerResponseFilter>> postMatchContainerResponseFilters = 
        new LinkedHashMap<NameKey, ProviderInfo<ContainerResponseFilter>>();
    private RequestPreprocessor requestPreprocessor;
    private ProviderInfo<Application> application;
    private List<DynamicFeature> dynamicFeatures = new LinkedList<DynamicFeature>();
    
    // This may be better be kept at OperationResourceInfo ? Though we may have many methods
    // across different resources that use the same BeanParam. 
    private Map<Class<?>, BeanParamInfo> beanParams = new HashMap<Class<?>, BeanParamInfo>();
    
    private ServerProviderFactory(ProviderFactory baseFactory, Bus bus) {
        super(baseFactory, bus);
    }
    
    public static ServerProviderFactory getInstance() {
        return createInstance(null);
    }
    
    public static ServerProviderFactory createInstance(Bus bus) {
        if (bus == null) {
            bus = BusFactory.getThreadDefaultBus();
        }
        ServerProviderFactory baseFactory = initBaseFactory(bus);
        ServerProviderFactory factory = new ServerProviderFactory(baseFactory, bus);
        factory.setBusProviders();
        return factory;
    }
    
    public static ServerProviderFactory getInstance(Message m) {
        Endpoint e = m.getExchange().get(Endpoint.class);
        return (ServerProviderFactory)e.get(SERVER_FACTORY_NAME);
    }
    
    private static synchronized ServerProviderFactory initBaseFactory(Bus bus) {
        ServerProviderFactory factory = (ServerProviderFactory)bus.getProperty(SHARED_SERVER_FACTORY);
        if (factory != null) {
            return factory;
        }
        factory = new ServerProviderFactory(null, bus);
        ProviderFactory.initBaseFactory(factory);
        factory.setProviders(new WebApplicationExceptionMapper(),
                             new WadlGenerator());
        bus.setProperty(SHARED_SERVER_FACTORY, factory);
        return factory;
    }
    
    public List<ProviderInfo<ContainerRequestFilter>> getPreMatchContainerRequestFilters() {
        return Collections.unmodifiableList(preMatchContainerRequestFilters);
    }
    
    public List<ProviderInfo<ContainerRequestFilter>> getPostMatchContainerRequestFilters(List<String> names) {
        return getPostMatchContainerFilters(postMatchContainerRequestFilters, 
                                            names);
    }
    
    public List<ProviderInfo<ContainerResponseFilter>> getContainerResponseFilters(List<String> names) {
        return getPostMatchContainerFilters(postMatchContainerResponseFilters, 
                                            names);
    }
    
    private static <T> List<ProviderInfo<T>> getPostMatchContainerFilters(Map<NameKey, ProviderInfo<T>> boundFilters,
                                                                          List<String> names) {
        
        if (boundFilters.isEmpty()) {
            return Collections.emptyList();
        }
        names = names == null ? Collections.<String>emptyList() : names;
        
        MultivaluedMap<ProviderInfo<T>, String> map = 
            new MetadataMap<ProviderInfo<T>, String>();
        for (Map.Entry<NameKey, ProviderInfo<T>> entry : boundFilters.entrySet()) {
            String entryName = entry.getKey().getName();
            if (entryName.equals(DEFAULT_FILTER_NAME_BINDING)) {
                map.put(entry.getValue(), Collections.<String>emptyList());
            } else {
                map.add(entry.getValue(), entryName);
            }
        }
        List<ProviderInfo<T>> list = new LinkedList<ProviderInfo<T>>();
        for (Map.Entry<ProviderInfo<T>, List<String>> entry : map.entrySet()) {
            if (names.containsAll(entry.getValue())) {
                list.add(entry.getKey());
            }
        }
        return list;
    }
    
    public List<ProviderInfo<RequestHandler>> getRequestHandlers() {
        List<ProviderInfo<RequestHandler>> handlers = null;
        if (requestHandlers.size() == 0) {
            handlers = ((ServerProviderFactory)getBaseFactory()).requestHandlers;
        } else {
            handlers = new ArrayList<ProviderInfo<RequestHandler>>();
            boolean customWADLHandler = false;
            for (int i = 0; i < requestHandlers.size(); i++) {
                if (requestHandlers.get(i).getProvider() instanceof WadlGenerator) {
                    customWADLHandler = true;
                    break;
                }
            }
            if (!customWADLHandler) {
                // TODO : this works only because we know we only have a single 
                // system handler which is a default WADLGenerator, think of a better approach
                handlers.addAll(((ServerProviderFactory)getBaseFactory()).requestHandlers);    
            }
            handlers.addAll(requestHandlers);
            
        }
        return Collections.unmodifiableList(handlers);
    }
    
    public List<ProviderInfo<ResponseHandler>> getResponseHandlers() {
        return Collections.unmodifiableList(responseHandlers);
    }
    
    public void addBeanParamInfo(BeanParamInfo bpi) {
        beanParams.put(bpi.getResourceClass(), bpi);
    }
    
    public BeanParamInfo getBeanParamInfo(Class<?> beanClass) {
        return beanParams.get(beanClass);
    }
   
    public <T extends Throwable> ExceptionMapper<T> createExceptionMapper(Class<?> exceptionType,
                                                                          Message m) {
        ExceptionMapper<T> mapper = doCreateExceptionMapper(exceptionType, m);
        if (mapper != null || isBaseFactory()) {
            return mapper;
        }
        
        return ((ServerProviderFactory)getBaseFactory()).createExceptionMapper(exceptionType, m);
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Throwable> ExceptionMapper<T> doCreateExceptionMapper(
        Class<?> exceptionType, Message m) {
        
        List<ExceptionMapper<?>> candidates = new LinkedList<ExceptionMapper<?>>();
        for (ProviderInfo<ExceptionMapper<?>> em : exceptionMappers) {
            handleMapper(candidates, em, exceptionType, m, ExceptionMapper.class, true);
        }
        if (candidates.size() == 0) {
            return null;
        }
        Collections.sort(candidates, new ExceptionMapperComparator());
        return (ExceptionMapper<T>) candidates.get(0);
    }
    
  //CHECKSTYLE:OFF 
    @Override
    protected void setProviders(Object... providers) {
        super.setProviders(providers);
        List<ProviderInfo<ContainerRequestFilter>> postMatchRequestFilters = 
            new LinkedList<ProviderInfo<ContainerRequestFilter>>();
        List<ProviderInfo<ContainerResponseFilter>> postMatchResponseFilters = 
            new LinkedList<ProviderInfo<ContainerResponseFilter>>();
        
        for (Object o : providers) {
            if (o == null) {
                continue;
            }
            Class<?> oClass = ClassHelper.getRealClass(o);
            
            
            if (RequestHandler.class.isAssignableFrom(oClass)) {
                requestHandlers.add(new ProviderInfo<RequestHandler>((RequestHandler)o, getBus())); 
            }
            
            if (ResponseHandler.class.isAssignableFrom(oClass)) {
                responseHandlers.add(new ProviderInfo<ResponseHandler>((ResponseHandler)o, getBus())); 
            }
            
            if (ContainerRequestFilter.class.isAssignableFrom(oClass)) {
                addContainerFilter(postMatchRequestFilters,
                   new ProviderInfo<ContainerRequestFilter>((ContainerRequestFilter)o, getBus()),
                   preMatchContainerRequestFilters);
            }
            
            if (ContainerResponseFilter.class.isAssignableFrom(oClass)) {
                addContainerFilter(postMatchResponseFilters,
                   new ProviderInfo<ContainerResponseFilter>((ContainerResponseFilter)o, getBus()),
                   null); 
            }
            
            if (DynamicFeature.class.isAssignableFrom(oClass)) {
                dynamicFeatures.add((DynamicFeature)o);
            }
            
            
            if (ExceptionMapper.class.isAssignableFrom(oClass)) {
                exceptionMappers.add(new ProviderInfo<ExceptionMapper<?>>((ExceptionMapper<?>)o, getBus())); 
            }
            
        }
        
        Collections.sort(preMatchContainerRequestFilters, new BindingPriorityComparator(true));
        mapContainerFilters(postMatchContainerRequestFilters, postMatchRequestFilters, true);
        mapContainerFilters(postMatchContainerResponseFilters, postMatchResponseFilters, false);
        
        injectContextProxies( 
            requestHandlers, responseHandlers, exceptionMappers,
            postMatchContainerRequestFilters.values(), preMatchContainerRequestFilters,
            postMatchContainerResponseFilters.values(),
            readerInterceptors, writerInterceptors);
    }
//CHECKSTYLE:ON
    
    private static <T> void addContainerFilter(List<ProviderInfo<T>> postMatchFilters,
                                               ProviderInfo<T> p,
                                               List<ProviderInfo<T>> preMatchFilters) {
        T filter = p.getProvider();
        if (preMatchFilters != null && isPrematching(filter.getClass())) {
            preMatchFilters.add(p);
        } else {
            postMatchFilters.add(p);
        }
        
    }
    
    public RequestPreprocessor getRequestPreprocessor() {
        return requestPreprocessor;
    }
    
    public void setApplicationProvider(ProviderInfo<Application> app) {
        application = app;
    }
    
    public void setRequestPreprocessor(RequestPreprocessor rp) {
        this.requestPreprocessor = rp;
    }
    
    public void clearExceptionMapperProxies() {
        clearProxies(exceptionMappers);
    }
    
    @Override
    public void clearProviders() {
        super.clearProviders();
        exceptionMappers.clear();
        requestHandlers.clear();
        responseHandlers.clear();
        postMatchContainerRequestFilters.clear();
        postMatchContainerResponseFilters.clear();
        preMatchContainerRequestFilters.clear();
    }
    
    @Override
    public void clearThreadLocalProxies() {
        if (application != null) {
            application.clearThreadLocalProxies();
        }
        super.clearThreadLocalProxies();
    }
    
    public void applyDynamicFeatures(List<ClassResourceInfo> list) {
        if (dynamicFeatures.size() > 0) {
            for (ClassResourceInfo cri : list) {
                doApplyDynamicFeatures(cri);
            }
        }
    }
    
    private void doApplyDynamicFeatures(ClassResourceInfo cri) {
        Set<OperationResourceInfo> oris = cri.getMethodDispatcher().getOperationResourceInfos();
        for (OperationResourceInfo ori : oris) {
            for (DynamicFeature feature : dynamicFeatures) {
                FeatureContext methodConfigurable = new MethodConfigurable(ori);
                feature.configure(new ResourceInfoImpl(ori), methodConfigurable);
            }
        }
        Collection<ClassResourceInfo> subs = cri.getSubResources();
        for (ClassResourceInfo sub : subs) {
            if (sub != cri) {
                doApplyDynamicFeatures(sub);    
            }
        }
    }
    
    protected static boolean isPrematching(Class<?> filterCls) {
        return AnnotationUtils.getAnnotation(filterCls.getAnnotations(), 
                                      PreMatching.class) != null;
    }
    
    private static <T> void mapContainerFilters(Map<NameKey, ProviderInfo<T>> map,
                                                List<ProviderInfo<T>> postMatchFilters,
                                                boolean ascending) {
        
        Collections.sort(postMatchFilters, new PostMatchFilterComparator(ascending));
        for (ProviderInfo<T> p : postMatchFilters) { 
            List<String> names = AnnotationUtils.getNameBindings(
                p.getProvider().getClass().getAnnotations());
            names = names.isEmpty() ? Collections.singletonList(DEFAULT_FILTER_NAME_BINDING) : names;
            for (String name : names) {
                map.put(new NameKey(name, AnnotationUtils.getBindingPriority(p.getProvider().getClass())), 
                        p);
            }
        }
        
    }
    
    private static class PostMatchFilterComparator extends BindingPriorityComparator {
        public PostMatchFilterComparator(boolean ascending) {
            super(ascending);
        }
        
        @Override
        public int compare(ProviderInfo<?> p1, ProviderInfo<?> p2) {
            int result = super.compare(p1, p2);
            if (result == 0) {
                Integer namesSize1 = 
                    AnnotationUtils.getNameBindings(p1.getProvider().getClass().getAnnotations()).size();
                Integer namesSize2 = 
                    AnnotationUtils.getNameBindings(p2.getProvider().getClass().getAnnotations()).size();
                
                // if we have two filters with the same binding priority, 
                // then put a filter with more name bindings upfront 
                // (this effectively puts name bound filters before global ones)
                result = namesSize1.compareTo(namesSize2) * -1;
            }
            return result; 
        }
    }
    
    private static class NameKey { 
        private String name;
        private int bindingPriority;
        public NameKey(String name, int priority) {
            this.name = name;
            this.bindingPriority = priority;
        }
        
        public String getName() {
            return name;
        }
        
        public int getPriority() {
            return bindingPriority;
        }
    }
    
    private class MethodConfigurable implements FeatureContext, Configuration {
        
        private OperationResourceInfo ori;
        private String nameBinding;
        private boolean bindingSet;
        
        public MethodConfigurable(OperationResourceInfo ori) {
            this.ori = ori;
            nameBinding = DEFAULT_FILTER_NAME_BINDING 
                + ori.getClassResourceInfo().getServiceClass().getName()
                + "."
                + ori.getMethodToInvoke().getName();
        }
        

        @Override
        public Configuration getConfiguration() {
            return this;
        }
        
        @Override
        public FeatureContext register(Object provider) {
            return register(provider, AnnotationUtils.getBindingPriority(provider.getClass()));
        }

        @Override
        public FeatureContext register(Object provider, int bindingPriority) {
            return doRegister(provider, bindingPriority, FILTER_INTERCEPTOR_CLASSES);
        }
        
        @Override
        public FeatureContext register(Object provider, Class<?>... contracts) {
            return register(provider, Priorities.USER, contracts);
        }
        
        //@Override
        public FeatureContext register(Object provider, int priority, Class<?>... contracts) {
            return register(provider, priority, contracts);
        }
        
        @Override
        public FeatureContext register(Object provider, Map<Class<?>, Integer> contracts) {
            for (Map.Entry<Class<?>, Integer> entry : contracts.entrySet()) {
                doRegister(provider, entry.getValue(), entry.getKey());
            }
            return this;
        }
        
        @Override
        public FeatureContext register(Class<?> providerClass) {
            return register(providerClass, AnnotationUtils.getBindingPriority(providerClass));
        }

        @Override
        public FeatureContext register(Class<?> providerClass, int bindingPriority) {
            return doRegister(createProvider(providerClass), bindingPriority, 
                              FILTER_INTERCEPTOR_CLASSES);
        }

        @Override
        public FeatureContext register(Class<?> providerClass, Class<?>... contracts) {
            return register(providerClass, Priorities.USER, contracts);
        }

        @Override
        public FeatureContext register(Class<?> providerClass, Map<Class<?>, Integer> contracts) {
            Object provider = createProvider(providerClass);
            for (Map.Entry<Class<?>, Integer> entry : contracts.entrySet()) {
                doRegister(provider, entry.getValue(), entry.getKey());
            }
            return this;
        }
        
        public FeatureContext replaceWith(Configuration config) {
            // nothing to do in this case
            return this;
        }
        
        //@Override
        public FeatureContext register(Class<?> providerClass, int bindingPriority,
                                         Class<?>... contracts) {
            return doRegister(createProvider(providerClass), bindingPriority, contracts);
        }
        
        private FeatureContext doRegister(Object provider, int bindingPriority, Class<?>... contracts) {
        
            if (provider instanceof Feature) {
                ((Feature)provider).configure(this);
                return this;
            }
            
            boolean setIsNeeded = false;
            for (Class<?> contract : contracts) {
                if (contract == ContainerRequestFilter.class && provider instanceof ContainerRequestFilter) {
                    if (isPrematching(provider.getClass())) {
                        addToInterceptors(preMatchContainerRequestFilters, provider, bindingPriority, true);
                    } else {
                        postMatchContainerRequestFilters = 
                            addToPostMatching(postMatchContainerRequestFilters, provider, bindingPriority, true);
                        setIsNeeded = true;
                    }
                }
                if (contract == ContainerResponseFilter.class && provider instanceof ContainerResponseFilter) {
                    postMatchContainerResponseFilters = 
                        addToPostMatching(postMatchContainerResponseFilters, provider, bindingPriority, false);
                    setIsNeeded = true;    
                }
                if (contract == ReaderInterceptor.class && provider instanceof ReaderInterceptor) {
                    addToInterceptors(readerInterceptors, provider, bindingPriority, true);
                }
                if (contract == WriterInterceptor.class && provider instanceof WriterInterceptor) {
                    addToInterceptors(writerInterceptors, provider, bindingPriority, false);
                }
            }
            
            if (setIsNeeded && !bindingSet) {
                ori.addNameBindings(Collections.singletonList(nameBinding));
                bindingSet = true;
            }

            return this;
        }
        
        @SuppressWarnings("unchecked")
        private <T> void addToInterceptors(List<ProviderInfo<T>> providers, Object provider, 
                                           int priority, boolean asc) {
            int size = providers.size();
            if (size > 0) {
                for (int i = 0; i < size; i++) {
                    int providerPriority = AnnotationUtils.getBindingPriority(
                        providers.get(i).getProvider().getClass());
                    if (asc) {
                        if (priority < providerPriority || i + 1 == size) {
                            int index = priority < providerPriority ? i : i + 1;
                            providers.add(index, (ProviderInfo<T>)newProvider(provider));
                            break;
                        }
                    } else if (priority > providerPriority || i + 1 == size) {
                        int index = priority > providerPriority ? i : i + 1; 
                        providers.add(index, (ProviderInfo<T>)newProvider(provider));
                        break;
                    }
                }
            } else {
                providers.add((ProviderInfo<T>)newProvider(provider));
            }
        }
        
        private <T> ProviderInfo<T> newProvider(T provider) {
            ProviderInfo<T> newProvider = new ProviderInfo<T>(provider, getBus());
            injectContextProxiesIntoProvider(newProvider);
            return newProvider;
        }
        
        @SuppressWarnings("unchecked")
        private <T> Map<NameKey, ProviderInfo<T>> addToPostMatching(
            Map<NameKey, ProviderInfo<T>> map, Object provider, int priority, boolean asc) {
            Map<NameKey, ProviderInfo<T>> newMap = new LinkedHashMap<NameKey, ProviderInfo<T>>();
            
            Iterator<Map.Entry<NameKey, ProviderInfo<T>>> it = map.entrySet().iterator();
            if (it.hasNext()) {
                boolean added = false;
                while (it.hasNext()) {
                    Map.Entry<NameKey, ProviderInfo<T>> entry = it.next();
                    int providerPriority = entry.getKey().getPriority();
                    // this surely can be collapsed further
                    if (!added && asc && (priority < providerPriority || !it.hasNext())) {
                        addNewProvider(newMap, entry, provider, priority, providerPriority >= priority);
                        added = true;
                    } else if (!added && !asc && (priority > providerPriority || !it.hasNext())) {
                        addNewProvider(newMap, entry, provider, priority, priority > providerPriority);
                        added = true;
                    } else {
                        newMap.put(entry.getKey(), entry.getValue());
                    }   
                }
            } else {
                newMap.put(new NameKey(nameBinding, priority), (ProviderInfo<T>)newProvider(provider));
            }
            return newMap;
            
                
        }
        
        @SuppressWarnings("unchecked")
        private <T> void addNewProvider(Map<NameKey, ProviderInfo<T>> newMap, 
                                        Map.Entry<NameKey, ProviderInfo<T>> entry,
                                        Object provider, 
                                        int priority,
                                        boolean first) {
            if (first) {
                newMap.put(new NameKey(nameBinding, priority), (ProviderInfo<T>)newProvider(provider));
                newMap.put(entry.getKey(), entry.getValue());
            } else {
                newMap.put(entry.getKey(), entry.getValue());
                newMap.put(new NameKey(nameBinding, priority), (ProviderInfo<T>)newProvider(provider));
            }
        }
        
        @Override
        public Set<Class<?>> getClasses() {
            // TODO Auto-generated method stub
            return null;
        }
        
        @Override
        public Map<Class<?>, Integer> getContracts(Class<?> componentClass) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Set<Object> getInstances() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Map<String, Object> getProperties() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean isEnabled(Feature feature) {
            return false;
        }
        
        @Override
        public boolean isEnabled(Class<? extends Feature> featureClass) {
            return false;
        }
        
        @Override
        public RuntimeType getRuntimeType() {
            return null;
        }
        
        @Override
        public Object getProperty(String name) {
            // TODO Auto-generated method stub
            return null;
        }
        
        @Override
        public Collection<String> getPropertyNames() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean isRegistered(Class<?> componentClass) {
            return false;
        }
        
        @Override
        public boolean isRegistered(Object component) {
            return false;
        }
        
        private Object createProvider(Class<?> cls) {
            try {
                return cls.newInstance();
            } catch (Throwable ex) {
                throw new RuntimeException(ex); 
            }
        }

        @Override
        public FeatureContext property(String arg0, Object arg1) {
            // TODO Auto-generated method stub
            return null;
        }
    }
    
    private static class ExceptionMapperComparator implements 
        Comparator<ExceptionMapper<? extends Throwable>> {
    
        public int compare(ExceptionMapper<? extends Throwable> em1, 
                           ExceptionMapper<? extends Throwable> em2) {
            return compareClasses(em1, em2);
        }
        
    }
}
