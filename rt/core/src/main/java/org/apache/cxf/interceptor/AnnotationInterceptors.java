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

package org.apache.cxf.interceptor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.feature.Features;
import org.apache.cxf.message.Message;

public class AnnotationInterceptors {
    
    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(AnnotationInterceptors.class);
    
    private Class<?> clazzes[];
    
    public AnnotationInterceptors(Class<?> ... clz) {
        clazzes = clz;
    }
        
    private <T> List<T> getAnnotationObject(Class<? extends Annotation> annotationClazz, Class<T> type) {
        
        for (Class<?> cls : clazzes) {
            Annotation  annotation = cls.getAnnotation(annotationClazz);
            if (annotation != null) {
                return initializeAnnotationObjects(getAnnotationObjectNames(annotation), type);
            }
        }
        return null;
    }
    
    private String[] getAnnotationObjectNames(Annotation ann) {
        if (ann instanceof InFaultInterceptors) {
            return ((InFaultInterceptors)ann).interceptors();
        } else if (ann instanceof InInterceptors) {
            return ((InInterceptors)ann).interceptors();
        } else if (ann instanceof OutFaultInterceptors) {
            return ((OutFaultInterceptors)ann).interceptors();
        } else if (ann instanceof OutInterceptors) {
            return ((OutInterceptors)ann).interceptors();
        } else if (ann instanceof Features) {
            return ((Features)ann).features();
        }
        
        throw new UnsupportedOperationException("Doesn't support the annotation: " + ann);
    }
    
    private <T> List<T> initializeAnnotationObjects(String[] annotationObjects, Class<T> type) {
        List<T> theAnnotationObjects = new ArrayList<T>();
        if (annotationObjects != null && annotationObjects.length > 0) {
            for (String annObjectName : annotationObjects) {
                Object object = null;
                try {
                    object = ClassLoaderUtils.loadClass(annObjectName, this.getClass()).newInstance();
                    theAnnotationObjects.add(type.cast(object));
                } catch (ClassNotFoundException e) {
                    throw new Fault(new org.apache.cxf.common.i18n.Message(
                                                    "COULD_NOT_CREATE_ANNOTATION_OBJECT", 
                                                    BUNDLE, annObjectName), e);
                } catch (InstantiationException ie) {
                    throw new Fault(new org.apache.cxf.common.i18n.Message(
                                                    "COULD_NOT_CREATE_ANNOTATION_OBJECT", 
                                                    BUNDLE, annObjectName), ie);
                } catch (IllegalAccessException iae) {
                    throw new Fault(new org.apache.cxf.common.i18n.Message(
                                                    "COULD_NOT_CREATE_ANNOTATION_OBJECT", 
                                                    BUNDLE, annObjectName), iae);
                } catch (ClassCastException ex) {
                    throw new Fault(new org.apache.cxf.common.i18n.Message(
                                                "COULD_NOT_CREATE_ANNOTATION_OBJECT", 
                                                BUNDLE, annObjectName), ex);
                }
            }
        }
        return theAnnotationObjects;
    }
    
    @SuppressWarnings("unchecked")
    private List<Interceptor<? extends Message>> getAnnotationInterceptorList(Class<? extends Annotation> t) {
        List<Interceptor> i = getAnnotationObject(t, Interceptor.class);
        if (i == null) {
            return null;
        }
        List<Interceptor<? extends Message>> m = new ArrayList<Interceptor<? extends Message>>();
        for (Interceptor i2 : i) {
            m.add(i2);
        }
        return m;
    }

    public List<Interceptor<? extends Message>> getInFaultInterceptors() {
        return getAnnotationInterceptorList(InFaultInterceptors.class);
    }

    public List<Interceptor<? extends Message>> getInInterceptors() {
        return getAnnotationInterceptorList(InInterceptors.class);
    }

    public List<Interceptor<? extends Message>> getOutFaultInterceptors() {
        return getAnnotationInterceptorList(OutFaultInterceptors.class);
    }

    public List<Interceptor<? extends Message>> getOutInterceptors() {
        return getAnnotationInterceptorList(OutInterceptors.class);
    }
        
    public List<AbstractFeature> getFeatures() {
        return getAnnotationObject(Features.class, AbstractFeature.class);
    }

}
