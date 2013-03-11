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

package org.apache.cxf.ws.security.wss4j.policyvalidators;

import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.security.transport.TLSSessionInfo;
import org.apache.cxf.ws.policy.AssertionInfo;
import org.apache.cxf.ws.policy.AssertionInfoMap;
import org.apache.wss4j.dom.WSSecurityEngineResult;
import org.apache.wss4j.policy.SP12Constants;
import org.apache.wss4j.policy.model.Layout;
import org.apache.wss4j.policy.model.Layout.LayoutType;
import org.apache.wss4j.policy.model.TransportBinding;

/**
 * Validate a TransportBinding policy.
 */
public class TransportBindingPolicyValidator extends AbstractBindingPolicyValidator {
    
    public boolean validatePolicy(
        AssertionInfoMap aim,
        Message message,
        Element soapBody,
        List<WSSecurityEngineResult> results,
        List<WSSecurityEngineResult> signedResults,
        List<WSSecurityEngineResult> encryptedResults
    ) {
        Collection<AssertionInfo> ais = aim.get(SP12Constants.TRANSPORT_BINDING);
        if (ais == null || ais.isEmpty()) {                       
            return true;
        }
        for (AssertionInfo ai : ais) {
            TransportBinding binding = (TransportBinding)ai.getAssertion();
            ai.setAsserted(true);
            
            // Check that TLS is in use if we are not the requestor
            boolean initiator = MessageUtils.isRequestor(message);
            TLSSessionInfo tlsInfo = message.get(TLSSessionInfo.class);
            if (!initiator && tlsInfo == null) {
                ai.setNotAsserted("TLS is not enabled");
                continue;
            }
            
            // HttpsToken is validated by the HttpsTokenInterceptorProvider
            if (binding.getTransportToken() != null) {
                assertPolicy(aim, binding.getTransportToken());
            }
            
            // Check the AlgorithmSuite
            AlgorithmSuitePolicyValidator algorithmValidator = new AlgorithmSuitePolicyValidator(results);
            if (!algorithmValidator.validatePolicy(ai, binding.getAlgorithmSuite())) {
                continue;
            }
            assertPolicy(aim, SP12Constants.ALGORITHM_SUITE);
            String namespace = binding.getAlgorithmSuite().getVersion().getNamespace();
            String name = binding.getAlgorithmSuite().getAlgorithmSuiteType().getName();
            Collection<AssertionInfo> algSuiteAis = aim.get(new QName(namespace, name));
            for (AssertionInfo algSuiteAi : algSuiteAis) {
                algSuiteAi.setAsserted(true);
            }
            
            // Check the IncludeTimestamp
            if (!validateTimestamp(binding.isIncludeTimestamp(), true, results, signedResults, message)) {
                String error = "Received Timestamp does not match the requirements";
                notAssertPolicy(aim, SP12Constants.INCLUDE_TIMESTAMP, error);
                ai.setNotAsserted(error);
                continue;
            }
            assertPolicy(aim, SP12Constants.INCLUDE_TIMESTAMP);
            
            // Check the Layout
            Layout layout = binding.getLayout();
            LayoutType layoutType = layout.getLayoutType();
            boolean timestampFirst = layoutType == LayoutType.LaxTsFirst;
            boolean timestampLast = layoutType == LayoutType.LaxTsLast;
            if (!validateLayout(aim, timestampFirst, timestampLast, results)) {
                String error = "Layout does not match the requirements";
                notAssertPolicy(aim, SP12Constants.LAYOUT, error);
                ai.setNotAsserted(error);
                continue;
            }
            assertPolicy(aim, SP12Constants.LAYOUT);
        }
        
        // We don't need to check these policies for the Transport binding
        assertPolicy(aim, SP12Constants.ENCRYPTED_PARTS);
        assertPolicy(aim, SP12Constants.SIGNED_PARTS);
        
        return true;
    }
    
}
