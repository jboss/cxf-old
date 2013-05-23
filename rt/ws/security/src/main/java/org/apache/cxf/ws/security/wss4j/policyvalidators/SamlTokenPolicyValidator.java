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

import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import org.apache.cxf.message.Message;
import org.apache.cxf.security.transport.TLSSessionInfo;
import org.apache.cxf.ws.policy.AssertionInfo;
import org.apache.cxf.ws.policy.AssertionInfoMap;
import org.apache.wss4j.common.saml.SamlAssertionWrapper;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.WSSecurityEngineResult;
import org.apache.wss4j.dom.saml.DOMSAMLUtil;
import org.apache.wss4j.dom.util.WSSecurityUtil;
import org.apache.wss4j.policy.SPConstants;
import org.apache.wss4j.policy.model.SamlToken;
import org.apache.wss4j.policy.model.SamlToken.SamlTokenType;
import org.opensaml.common.SAMLVersion;

/**
 * Validate a SamlToken policy.
 */
public class SamlTokenPolicyValidator extends AbstractSamlPolicyValidator implements TokenPolicyValidator {
    
    private Element body;
    private List<WSSecurityEngineResult> signed;
    
    public boolean validatePolicy(
        AssertionInfoMap aim,
        Message message,
        Element soapBody,
        List<WSSecurityEngineResult> results,
        List<WSSecurityEngineResult> signedResults
    ) {
        body = soapBody;
        signed = signedResults;
        
        Collection<AssertionInfo> ais = getAllAssertionsByLocalname(aim, SPConstants.SAML_TOKEN);
        if (!ais.isEmpty()) {
            parsePolicies(aim, ais, message, results, signedResults);
        }
        
        return true;
    }
    
    private void parsePolicies(
        AssertionInfoMap aim, 
        Collection<AssertionInfo> ais, 
        Message message,
        List<WSSecurityEngineResult> results,
        List<WSSecurityEngineResult> signedResults
    ) {
        final List<Integer> actions = new ArrayList<Integer>(2);
        actions.add(WSConstants.ST_SIGNED);
        actions.add(WSConstants.ST_UNSIGNED);
        List<WSSecurityEngineResult> samlResults = 
            WSSecurityUtil.fetchAllActionResults(results, actions);
        
        for (AssertionInfo ai : ais) {
            SamlToken samlToken = (SamlToken)ai.getAssertion();
            ai.setAsserted(true);

            if (!isTokenRequired(samlToken, message)) {
                assertPolicy(
                    aim, 
                    new QName(samlToken.getVersion().getNamespace(), samlToken.getSamlTokenType().name())
                );
                continue;
            }

            if (samlResults.isEmpty()) {
                ai.setNotAsserted(
                    "The received token does not match the token inclusion requirement"
                );
                continue;
            }
            
            // All of the received SAML Assertions must conform to the policy
            for (WSSecurityEngineResult result : samlResults) {
                SamlAssertionWrapper assertionWrapper = 
                    (SamlAssertionWrapper)result.get(WSSecurityEngineResult.TAG_SAML_ASSERTION);
                
                if (!checkVersion(aim, samlToken, assertionWrapper)) {
                    ai.setNotAsserted("Wrong SAML Version");
                    continue;
                }
                TLSSessionInfo tlsInfo = message.get(TLSSessionInfo.class);
                Certificate[] tlsCerts = null;
                if (tlsInfo != null) {
                    tlsCerts = tlsInfo.getPeerCertificates();
                }
                if (!checkHolderOfKey(assertionWrapper, signedResults, tlsCerts)) {
                    ai.setNotAsserted("Assertion fails holder-of-key requirements");
                    continue;
                }
                if (!DOMSAMLUtil.checkSenderVouches(assertionWrapper, tlsCerts, body, signed)) {
                    ai.setNotAsserted("Assertion fails sender-vouches requirements");
                    continue;
                }
                /*
                    if (!checkIssuerName(samlToken, assertionWrapper)) {
                        ai.setNotAsserted("Wrong IssuerName");
                    }
                 */
            }
        }
    }
    
    /**
     * Check the IssuerName policy against the received assertion
    private boolean checkIssuerName(SamlToken samlToken, AssertionWrapper assertionWrapper) {
        String issuerName = samlToken.getIssuerName();
        if (issuerName != null && !"".equals(issuerName)) {
            String assertionIssuer = assertionWrapper.getIssuerString();
            if (!issuerName.equals(assertionIssuer)) {
                return false;
            }
        }
        return true;
    }
    */
    
    /**
     * Check the policy version against the received assertion
     */
    private boolean checkVersion(
        AssertionInfoMap aim,
        SamlToken samlToken, 
        SamlAssertionWrapper assertionWrapper
    ) {
        SamlTokenType samlTokenType = samlToken.getSamlTokenType();
        if ((samlTokenType == SamlTokenType.WssSamlV11Token10
            || samlTokenType == SamlTokenType.WssSamlV11Token11)
            && assertionWrapper.getSamlVersion() != SAMLVersion.VERSION_11) {
            return false;
        } else if (samlTokenType == SamlTokenType.WssSamlV20Token11
            && assertionWrapper.getSamlVersion() != SAMLVersion.VERSION_20) {
            return false;
        }
        
        assertPolicy(aim, new QName(samlToken.getVersion().getNamespace(), samlTokenType.name()));
        return true;
    }
    
}
