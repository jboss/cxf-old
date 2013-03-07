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

import org.apache.cxf.message.Message;
import org.apache.cxf.ws.policy.AssertionInfo;
import org.apache.cxf.ws.policy.AssertionInfoMap;
import org.apache.wss4j.dom.WSSecurityEngineResult;
import org.apache.wss4j.policy.SP12Constants;
import org.apache.wss4j.policy.model.AbstractToken;
import org.apache.wss4j.policy.model.AbstractToken.DerivedKeys;
import org.apache.wss4j.policy.model.IssuedToken;
import org.apache.wss4j.policy.model.KerberosToken;
import org.apache.wss4j.policy.model.KeyValueToken;
import org.apache.wss4j.policy.model.SamlToken;
import org.apache.wss4j.policy.model.SecurityContextToken;
import org.apache.wss4j.policy.model.SupportingTokens;
import org.apache.wss4j.policy.model.UsernameToken;
import org.apache.wss4j.policy.model.X509Token;

/**
 * Validate a SignedEndorsingSupportingToken policy. 
 */
public class SignedEndorsingTokenPolicyValidator extends AbstractSupportingTokenPolicyValidator {
    
    public SignedEndorsingTokenPolicyValidator() {
        setSigned(true);
        setEndorsed(true);
    }
    
    public boolean validatePolicy(
        AssertionInfoMap aim, 
        Message message,
        List<WSSecurityEngineResult> results,
        List<WSSecurityEngineResult> signedResults,
        List<WSSecurityEngineResult> encryptedResults
    ) {
        Collection<AssertionInfo> ais = aim.get(SP12Constants.SIGNED_ENDORSING_SUPPORTING_TOKENS);
        if (ais == null || ais.isEmpty()) {                       
            return true;
        }
        
        setMessage(message);
        setResults(results);
        setSignedResults(signedResults);
        setEncryptedResults(encryptedResults);

        for (AssertionInfo ai : ais) {
            SupportingTokens binding = (SupportingTokens)ai.getAssertion();
            ai.setAsserted(true);
            
            setSignedParts(binding.getSignedParts());
            setEncryptedParts(binding.getEncryptedParts());
            setSignedElements(binding.getSignedElements());
            setEncryptedElements(binding.getEncryptedElements());

            List<AbstractToken> tokens = binding.getTokens();
            for (AbstractToken token : tokens) {
                if (!isTokenRequired(token, message)) {
                    continue;
                }
                
                DerivedKeys derivedKeys = token.getDerivedKeys();
                setDerived(derivedKeys == DerivedKeys.RequireDerivedKeys);
                boolean processingFailed = false;
                if (token instanceof KerberosToken) {
                    if (!processKerberosTokens()) {
                        processingFailed = true;
                    }
                } else if (token instanceof SamlToken) {
                    if (!processSAMLTokens()) {
                        processingFailed = true;
                    }
                } else if (token instanceof X509Token) {
                    if (!processX509Tokens()) {
                        processingFailed = true;
                    }
                } else if (token instanceof KeyValueToken) {
                    if (!processKeyValueTokens()) {
                        processingFailed = true;
                    }
                } else if (token instanceof UsernameToken) {
                    if (!processUsernameTokens()) {
                        processingFailed = true;
                    }
                } else if (token instanceof SecurityContextToken) {
                    if (!processSCTokens()) {
                        processingFailed = true;
                    }
                } else if (!(token instanceof IssuedToken)) {
                    processingFailed = true;
                }
                
                if (processingFailed) {
                    ai.setNotAsserted(
                        "The received token does not match the signed endorsing supporting token requirement"
                    );
                    continue;
                }
            }
        }
        
        return true;
    }
    
}
