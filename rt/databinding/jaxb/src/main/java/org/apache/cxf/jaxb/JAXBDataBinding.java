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

package org.apache.cxf.jaxb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.sun.xml.bind.marshaller.NamespacePrefixMapper;
import com.sun.xml.bind.v2.ContextFactory;
import com.sun.xml.bind.v2.runtime.JAXBContextImpl;

import org.apache.cxf.common.i18n.Message;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.CacheMap;
import org.apache.cxf.common.util.PackageUtils;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.common.xmlschema.SchemaCollection;
import org.apache.cxf.databinding.DataBinding;
import org.apache.cxf.databinding.DataReader;
import org.apache.cxf.databinding.DataWriter;
import org.apache.cxf.databinding.source.AbstractDataBinding;
import org.apache.cxf.jaxb.io.DataReaderImpl;
import org.apache.cxf.jaxb.io.DataWriterImpl;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.factory.ServiceConstructionException;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.ws.addressing.ObjectFactory;

public class JAXBDataBinding extends AbstractDataBinding implements DataBinding {
    public static final String SCHEMA_RESOURCE = "SCHEMRESOURCE";

    public static final String UNWRAP_JAXB_ELEMENT = "unwrap.jaxb.element";

    private static final Logger LOG = LogUtils.getLogger(JAXBDataBinding.class);

    private static final Class<?> SUPPORTED_READER_FORMATS[] = new Class<?>[] {Node.class,
                                                                               XMLEventReader.class,
                                                                               XMLStreamReader.class};
    private static final Class<?> SUPPORTED_WRITER_FORMATS[] = new Class<?>[] {OutputStream.class,
                                                                               Node.class,
                                                                               XMLEventWriter.class,
                                                                               XMLStreamWriter.class};

    private static final class CachedContextAndSchemas {
        private JAXBContext context;
        private Collection<DOMSource> schemas;

        CachedContextAndSchemas(JAXBContext context) {
            this.context = context;
        }

        public JAXBContext getContext() {
            return context;
        }

        public Collection<DOMSource> getSchemas() {
            return schemas;
        }

        public void setSchemas(Collection<DOMSource> schemas) {
            this.schemas = schemas;
        }

    }
    
    private static final class CachedClassOrNull {
        private WeakReference<Class<?>> cachedClass;

        public CachedClassOrNull(Class<?> cachedClass) {
            this.cachedClass = new WeakReference<Class<?>>(cachedClass);
        }

        public Class<?> getCachedClass() {
            return cachedClass == null ? null : cachedClass.get();
        }

        public void setCachedClass(Class<?> cachedClass) {
            this.cachedClass = new WeakReference<Class<?>>(cachedClass);
        }
    }

    private static final Map<Set<Class<?>>, CachedContextAndSchemas> JAXBCONTEXT_CACHE 
        = new CacheMap<Set<Class<?>>, CachedContextAndSchemas>();
    
    private static final Map<Package, CachedClassOrNull> OBJECT_FACTORY_CACHE
        = new CacheMap<Package, CachedClassOrNull>();

    Class[] extraClass;

    JAXBContext context;
    Set<Class<?>> contextClasses;

    Class<?> cls;

    private Map<String, Object> contextProperties = Collections.emptyMap();
    private Map<String, Object> marshallerProperties = Collections.emptyMap();

    private boolean qualifiedSchemas;
    private Service service;

    public JAXBDataBinding() {
    }

    public JAXBDataBinding(boolean q) {
        this.qualifiedSchemas = q;
    }

    public JAXBDataBinding(Class<?>... classes) throws JAXBException {
        contextClasses = new LinkedHashSet<Class<?>>();
        contextClasses.addAll(Arrays.asList(classes));
        setContext(createJAXBContext(contextClasses)); //NOPMD - specifically allow this
    }

    public JAXBDataBinding(JAXBContext context) {
        this();
        setContext(context);
    }

    public JAXBContext getContext() {
        return context;
    }

    public final void setContext(JAXBContext ctx) {
        context = ctx;
    }

    private NamespacePrefixMapper getNamespacePrefixMapper() {
        Map<String, String> mappings = getDeclaredNamespaceMappings();
        if (mappings == null) {
            mappings = Collections.emptyMap();
        }

        final Map<String, String> closedMappings = mappings;

        NamespacePrefixMapper mapper = new NamespacePrefixMapper() {
            @Override
            public String getPreferredPrefix(String namespaceUri, String suggestion, boolean requirePrefix) {
                String prefix = closedMappings.get(namespaceUri);
                if (prefix != null) {
                    return prefix;
                }
                return suggestion;
            }
        };
        return mapper;
    }

    @SuppressWarnings("unchecked")
    public <T> DataWriter<T> createWriter(Class<T> c) {
        Map<String, Object> currentMarshallerProperties = new HashMap<String, Object>();
        if (!marshallerProperties.containsKey("com.sun.xml.bind.namespacePrefixMapper")) {
            currentMarshallerProperties.put("com.sun.xml.bind.namespacePrefixMapper",
                                            getNamespacePrefixMapper());
        }
        currentMarshallerProperties.putAll(marshallerProperties);
        if (c == XMLStreamWriter.class) {
            return (DataWriter<T>)new DataWriterImpl<XMLStreamWriter>(context, currentMarshallerProperties);
        } else if (c == OutputStream.class) {
            return (DataWriter<T>)new DataWriterImpl<OutputStream>(context, 
                currentMarshallerProperties);            
        } else if (c == XMLEventWriter.class) {
            return (DataWriter<T>)new DataWriterImpl<XMLEventWriter>(context,
                                                                     currentMarshallerProperties);           
        } else if (c == Node.class) {
            return (DataWriter<T>)new DataWriterImpl<Node>(context, currentMarshallerProperties);      
        }
        
        return null;
    }

    public Class<?>[] getSupportedWriterFormats() {
        return SUPPORTED_WRITER_FORMATS;
    }

    @SuppressWarnings("unchecked")
    public <T> DataReader<T> createReader(Class<T> c) {
        DataReader<T> dr = null;
        if (c == XMLStreamReader.class) {
            dr = (DataReader<T>)new DataReaderImpl<XMLStreamReader>(context);
        } else if (c == XMLEventReader.class) {
            dr = (DataReader<T>)new DataReaderImpl<XMLEventReader>(context);
        } else if (c == Node.class) {
            dr = (DataReader<T>)new DataReaderImpl<Node>(context);
        }

        return dr;
    }

    public Class<?>[] getSupportedReaderFormats() {
        return SUPPORTED_READER_FORMATS;
    }

    @SuppressWarnings("unchecked")
    public void initialize(Service aservice) {
        this.service = aservice;
        // context is already set, don't redo it
        if (context != null) {
            return;
        }


        contextClasses = new LinkedHashSet<Class<?>>();
        for (ServiceInfo serviceInfo : service.getServiceInfos()) {
            JAXBContextInitializer initializer = new JAXBContextInitializer(serviceInfo, contextClasses);
            initializer.walk();
            if (serviceInfo.getProperty("extra.class") != null) {
                Set<Class<?>> exClasses = serviceInfo.getProperty("extra.class", Set.class);
                contextClasses.addAll(exClasses);
            }

        }

        String tns = service.getName().getNamespaceURI();
        CachedContextAndSchemas cachedContextAndSchemas = null;
        JAXBContext ctx = null;
        try {
            if (service.getServiceInfos().size() > 0) {
                tns = service.getServiceInfos().get(0).getInterface().getName().getNamespaceURI();
            }
            cachedContextAndSchemas = createJAXBContextAndSchemas(contextClasses, tns);
        } catch (JAXBException e1) {
            // load jaxb needed class and try to create jaxb context for more
            // times
            boolean added = addJaxbObjectFactory(e1);
            while (cachedContextAndSchemas == null && added) {
                try {
                    ctx = JAXBContext.newInstance(contextClasses
                                                  .toArray(new Class[contextClasses.size()]), null);
                    cachedContextAndSchemas = new CachedContextAndSchemas(ctx);
                } catch (JAXBException e) {
                    e1 = e;
                    added = addJaxbObjectFactory(e1);
                }
            }

            if (ctx == null) {
                throw new ServiceConstructionException(e1);
            } else {
                synchronized (JAXBCONTEXT_CACHE) {
                    JAXBCONTEXT_CACHE.put(contextClasses, cachedContextAndSchemas);
                }                
            }
        }
        ctx = cachedContextAndSchemas.getContext();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "CREATED_JAXB_CONTEXT", new Object[] {ctx, contextClasses});
        }
        setContext(ctx);

        for (ServiceInfo serviceInfo : service.getServiceInfos()) {
            SchemaCollection col = serviceInfo.getXmlSchemaCollection();

            if (col.getXmlSchemas().length > 1) {
                // someone has already filled in the types
                continue;
            }

            boolean schemasFromCache = false;
            Collection<DOMSource> schemas = getSchemas();
            if (schemas == null) {
                schemas = cachedContextAndSchemas.getSchemas();
                if (schemas != null) {
                    schemasFromCache = true;
                }
            } else {
                schemasFromCache = true;
            }
            if (schemas == null) {
                schemas = new HashSet<DOMSource>();
                try {
                    for (DOMResult r : generateJaxbSchemas()) {
                        schemas.add(new DOMSource(r.getNode()));
                    }
                } catch (IOException e) {
                    throw new ServiceConstructionException(new Message("SCHEMA_GEN_EXC", LOG), e);
                }
            }
            for (DOMSource r : schemas) {
                addSchemaDocument(serviceInfo, 
                                  col, 
                                 (Document)r.getNode(),
                                  r.getSystemId());
            }

            JAXBContextImpl riContext;
            if (context instanceof JAXBContextImpl) {
                riContext = (JAXBContextImpl)context;
            } else {
                // fall back if we're using another jaxb implementation
                try {
                    riContext = (JAXBContextImpl)ContextFactory.createContext(contextClasses
                        .toArray(new Class[contextClasses.size()]), null);
                } catch (JAXBException e) {
                    throw new ServiceConstructionException(e);
                }
            }

            JAXBSchemaInitializer schemaInit = new JAXBSchemaInitializer(serviceInfo, col, riContext,
                                                                         this.qualifiedSchemas);
            schemaInit.walk();
            if (cachedContextAndSchemas != null && !schemasFromCache) {
                cachedContextAndSchemas.setSchemas(schemas);
            }
        }
    }
    
    public void setExtraClass(Class[] userExtraClass) {
        extraClass = userExtraClass;
    }

    public Class[] getExtraClass() {
        return extraClass;
    }

    private List<DOMResult> generateJaxbSchemas() throws IOException {
        final List<DOMResult> results = new ArrayList<DOMResult>();

        context.generateSchema(new SchemaOutputResolver() {
            private Map<String, String> builtIns = new HashMap<String, String>();
            {
                builtIns.put("http://www.w3.org/2005/02/addressing/wsdl",
                             "classpath:/schemas/wsdl/ws-addr-wsdl.xsd");
                builtIns.put("http://www.w3.org/2005/08/addressing", "classpath:/schemas/wsdl/ws-addr.xsd");
                builtIns.put("http://schemas.xmlsoap.org/ws/2005/02/rm", "classpath:/schemas/wsdl/wsrm.xsd");
                builtIns.put("http://www.w3.org/2005/05/xmlmime", "classpath:/schemas/wsdl/ws-addr.xsd");
            }

            @Override
            public Result createOutput(String ns, String file) throws IOException {
                DOMResult result = new DOMResult();

                if (builtIns.containsKey(ns)) {
                    result.setSystemId(builtIns.get(ns));
                    return result;
                }
                result.setSystemId(file);
                results.add(result);
                return result;
            }
        });

        return results;
    }

    public JAXBContext createJAXBContext(Set<Class<?>> classes) throws JAXBException {
        return createJAXBContext(classes, null);
    }

    public JAXBContext createJAXBContext(Set<Class<?>> classes, String defaultNs) throws JAXBException {
        return createJAXBContextAndSchemas(classes, defaultNs).getContext();
    }
    
    public CachedContextAndSchemas createJAXBContextAndSchemas(Set<Class<?>> classes,
                                                               String defaultNs) 
        throws JAXBException {
        
        // add user extra class into jaxb context
        if (extraClass != null && extraClass.length > 0) {
            for (Class clz : extraClass) {
                classes.add(clz);
            }
        }

        // try and read any jaxb.index files that are with the other classes.
        // This should
        // allow loading of extra classes (such as subclasses for inheritance
        // reasons)
        // that are in the same package. Also check for ObjectFactory classes
        Map<String, InputStream> packages = new HashMap<String, InputStream>();
        Map<String, ClassLoader> packageLoaders = new HashMap<String, ClassLoader>();
        Set<Class<?>> objectFactories = new HashSet<Class<?>>();
        for (Class<?> jcls : classes) {
            String pkgName = PackageUtils.getPackageName(jcls);
            if (!packages.containsKey(pkgName)) {
                Package pkg = jcls.getPackage();
                
                packages.put(pkgName, jcls.getResourceAsStream("jaxb.index"));
                packageLoaders.put(pkgName, jcls.getClassLoader());
                String objectFactoryClassName = pkgName + "." + "ObjectFactory";
                Class<?> ofactory = null;
                CachedClassOrNull cachedFactory = null;
                if (pkg != null) {
                    synchronized (OBJECT_FACTORY_CACHE) {
                        cachedFactory = OBJECT_FACTORY_CACHE.get(pkg);
                    }
                }
                if (cachedFactory != null) {
                    ofactory = cachedFactory.getCachedClass();
                }
                if (ofactory == null) {
                    try {
                        ofactory = Class.forName(objectFactoryClassName, false, jcls
                                                 .getClassLoader());
                        objectFactories.add(ofactory);
                        addToObjectFactoryCache(pkg, ofactory);
                    } catch (ClassNotFoundException e) {
                        addToObjectFactoryCache(pkg, null);
                    }
                } else {
                    objectFactories.add(ofactory);                    
                }
            }
        }
        for (Map.Entry<String, InputStream> entry : packages.entrySet()) {
            if (entry.getValue() != null) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(entry.getValue(),
                                                                                     "UTF-8"));
                    String pkg = entry.getKey();
                    ClassLoader loader = packageLoaders.get(pkg);
                    if (!StringUtils.isEmpty(pkg)) {
                        pkg += ".";
                    }

                    String line = reader.readLine();
                    while (line != null) {
                        line = line.trim();
                        if (line.indexOf("#") != -1) {
                            line = line.substring(0, line.indexOf("#"));
                        }
                        if (!StringUtils.isEmpty(line)) {
                            try {
                                Class<?> ncls = Class.forName(pkg + line, false, loader);
                                classes.add(ncls);
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                        line = reader.readLine();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    try {
                        entry.getValue().close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
        classes.addAll(objectFactories);
        addWsAddressingTypes(classes);

        for (Class<?> clz : classes) {
            if (clz.getName().endsWith("ObjectFactory")
                && checkObjectFactoryNamespaces(clz)) {
                // kind of a hack, but ObjectFactories may be created with empty
                // namespaces
                defaultNs = null;
            }
        }

        Map<String, Object> map = new HashMap<String, Object>();
        if (defaultNs != null) {
            map.put("com.sun.xml.bind.defaultNamespaceRemap", defaultNs);
        }

        if (contextProperties != null) {
            // add any specified context properties into the properties map
            map.putAll(contextProperties);
        }

        CachedContextAndSchemas cachedContextAndSchemas = null;
        synchronized (JAXBCONTEXT_CACHE) {
            cachedContextAndSchemas = JAXBCONTEXT_CACHE.get(classes);
        }
        if (cachedContextAndSchemas == null) {
            JAXBContext ctx = JAXBContext.newInstance(classes.toArray(new Class[classes.size()]), map);
            cachedContextAndSchemas = new CachedContextAndSchemas(ctx);
            synchronized (JAXBCONTEXT_CACHE) {
                JAXBCONTEXT_CACHE.put(classes, cachedContextAndSchemas);
            }
        }

        return cachedContextAndSchemas;
    }

    private boolean checkObjectFactoryNamespaces(Class<?> clz) {
        for (Method meth : clz.getMethods()) {
            XmlElementDecl decl = meth.getAnnotation(XmlElementDecl.class);
            if (decl != null 
                && StringUtils.isEmpty(decl.namespace())) {
                return true;
            }
        }

        return false;
    }

    private void addToObjectFactoryCache(Package objectFactoryPkg, Class<?> ofactory) {
        if (objectFactoryPkg == null) {
            return;
        }
        synchronized (OBJECT_FACTORY_CACHE) {
            OBJECT_FACTORY_CACHE.put(objectFactoryPkg, 
                                     new CachedClassOrNull(ofactory));
        }
    }

    private void addWsAddressingTypes(Set<Class<?>> classes) {
        if (classes.contains(ObjectFactory.class)) {
            // ws-addressing is used, lets add the specific types
            try {
                classes.add(Class.forName("org.apache.cxf.ws.addressing.wsdl.ObjectFactory"));
                classes.add(Class.forName("org.apache.cxf.ws.addressing.wsdl.AttributedQNameType"));
                classes.add(Class.forName("org.apache.cxf.ws.addressing.wsdl.ServiceNameType"));
            } catch (ClassNotFoundException unused) {
                // REVISIT - ignorable if WS-ADDRESSING not available?
                // maybe add a way to allow interceptors to add stuff to the
                // context?
            }
        }
    }

    // Now we can not add all the classes that Jaxb needed into JaxbContext,
    // especially when
    // an ObjectFactory is pointed to by an jaxb @XmlElementDecl annotation
    // added this workaround method to load the jaxb needed ObjectFactory class
    public boolean addJaxbObjectFactory(JAXBException e1) {
        boolean added = false;
        java.io.ByteArrayOutputStream bout = new java.io.ByteArrayOutputStream();
        java.io.PrintStream pout = new java.io.PrintStream(bout);
        e1.printStackTrace(pout);
        String str = new String(bout.toByteArray());
        Pattern pattern = Pattern.compile("(?<=There's\\sno\\sObjectFactory\\swith\\san\\s"
                                          + "@XmlElementDecl\\sfor\\sthe\\selement\\s\\{)\\S*(?=\\})");
        java.util.regex.Matcher matcher = pattern.matcher(str);
        while (matcher.find()) {
            String pkgName = JAXBUtils.namespaceURIToPackage(matcher.group());
            try {
                Class clz = getClass().getClassLoader().loadClass(pkgName + "." + "ObjectFactory");

                if (!contextClasses.contains(clz)) {
                    contextClasses.add(clz);
                    added = true;
                }
            } catch (ClassNotFoundException e) {
                // do nothing
            }

        }
        return added;
    }

    /**
     * Return a map of properties. These properties are passed to
     * JAXBContext.newInstance when this object creates a context.
     * 
     * @return the map of JAXB context properties.
     */
    public Map<String, Object> getContextProperties() {
        return contextProperties;
    }

    /**
     * Set a map of JAXB context properties. These properties are passed to
     * JAXBContext.newInstance when this object creates a context. Note that if
     * you create a JAXB context elsewhere, you will not respect these
     * properties unless you handle it manually.
     * 
     * @param contextProperties map of properties.
     */
    public void setContextProperties(Map<String, Object> contextProperties) {
        this.contextProperties = contextProperties;
    }

    /**
     * Return a map of properties. These properties are set into the JAXB
     * Marshaller (via Marshaller.setProperty(...) when the marshaller is
     * created.
     * 
     * @return the map of JAXB marshaller properties.
     */
    public Map<String, Object> getMarshallerProperties() {
        return marshallerProperties;
    }

    /**
     * Set a map of JAXB marshaller properties. These properties are set into
     * the JAXB Marshaller (via Marshaller.setProperty(...) when the marshaller
     * is created.
     * 
     * @param marshallerProperties map of properties.
     */
    public void setMarshallerProperties(Map<String, Object> marshallerProperties) {
        this.marshallerProperties = marshallerProperties;
    }
    
    
    public static void clearCaches() {
        synchronized (JAXBCONTEXT_CACHE) {
            JAXBCONTEXT_CACHE.clear();
        }
        synchronized (OBJECT_FACTORY_CACHE) {
            OBJECT_FACTORY_CACHE.clear();
        }
    }
}
