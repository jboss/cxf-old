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

package org.apache.cxf.ws.security.wss4j.policyhandlers;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.soap.SOAPException;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.ws.policy.AssertionInfo;
import org.apache.cxf.ws.policy.AssertionInfoMap;
import org.apache.wss4j.common.ConfigurationConstants;
import org.apache.wss4j.policy.SPConstants;
import org.apache.wss4j.policy.model.AbstractToken;
import org.apache.wss4j.policy.model.Header;
import org.apache.wss4j.policy.model.IssuedToken;
import org.apache.wss4j.policy.model.KeyValueToken;
import org.apache.wss4j.policy.model.SamlToken;
import org.apache.wss4j.policy.model.SignedElements;
import org.apache.wss4j.policy.model.SignedParts;
import org.apache.wss4j.policy.model.SupportingTokens;
import org.apache.wss4j.policy.model.TransportBinding;
import org.apache.wss4j.policy.model.TransportToken;
import org.apache.wss4j.policy.model.UsernameToken;
import org.apache.wss4j.policy.model.X509Token;
import org.apache.wss4j.stax.ext.WSSConstants;

/**
 * 
 */
public class StaxTransportBindingHandler extends AbstractStaxBindingHandler {
    
    private static final Logger LOG = LogUtils.getL7dLogger(StaxTransportBindingHandler.class);

    public StaxTransportBindingHandler(Map<String, Object> properties, SoapMessage msg) {
        super(properties, msg);
    }
    
    public void handleBinding() {
        AssertionInfoMap aim = getMessage().get(AssertionInfoMap.class);
        configureTimestamp(aim);
        configureLayout(aim);
        
        if (this.isRequestor()) {
            TransportBinding binding = (TransportBinding)getBinding(aim);
            TransportToken token = binding.getTransportToken();
            if (token.getToken() instanceof IssuedToken) {
                // TODO
            }
            
            try {
                handleNonEndorsingSupportingTokens(aim);
                handleEndorsingSupportingTokens(aim);
            } catch (Exception e) {
                LOG.log(Level.FINE, e.getMessage(), e);
                throw new Fault(e);
            }
        } else {
            addSignatureConfirmation(null);
        }
    }
    
    /**
     * Handle the non-endorsing supporting tokens
     */
    private void handleNonEndorsingSupportingTokens(AssertionInfoMap aim) throws Exception {
        Collection<AssertionInfo> ais;
        
        ais = getAllAssertionsByLocalname(aim, SPConstants.SIGNED_SUPPORTING_TOKENS);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                SupportingTokens sgndSuppTokens = (SupportingTokens)ai.getAssertion();
                if (sgndSuppTokens != null) {
                    addSignedSupportingTokens(sgndSuppTokens);
                }
                ai.setAsserted(true);
            }
        }
        
        ais = getAllAssertionsByLocalname(aim, SPConstants.SIGNED_ENCRYPTED_SUPPORTING_TOKENS);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                SupportingTokens sgndSuppTokens = (SupportingTokens)ai.getAssertion();
                if (sgndSuppTokens != null) {
                    addSignedSupportingTokens(sgndSuppTokens);
                }
                ai.setAsserted(true);
            }
        }
        
        ais = getAllAssertionsByLocalname(aim, SPConstants.ENCRYPTED_SUPPORTING_TOKENS);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                SupportingTokens encrSuppTokens = (SupportingTokens)ai.getAssertion();
                if (encrSuppTokens != null) {
                    addSignedSupportingTokens(encrSuppTokens);
                }
                ai.setAsserted(true);
            }
        }
        
        ais = getAllAssertionsByLocalname(aim, SPConstants.SUPPORTING_TOKENS);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                SupportingTokens suppTokens = (SupportingTokens)ai.getAssertion();
                if (suppTokens != null && suppTokens.getTokens() != null 
                    && suppTokens.getTokens().size() > 0) {
                    handleSupportingTokens(suppTokens, false);
                }
                ai.setAsserted(true);
            }
        }
    }
    
    private void addSignedSupportingTokens(SupportingTokens sgndSuppTokens) 
        throws Exception {
        for (AbstractToken token : sgndSuppTokens.getTokens()) {
            if (token instanceof UsernameToken) {
                addUsernameToken((UsernameToken)token);
            /*TODO 
              else if (token instanceof IssuedToken || token instanceof KerberosToken) {
                SecurityToken secTok = getSecurityToken();
                
                if (includeToken(token.getIncludeTokenType())) {
                    //Add the token
                    addEncryptedKeyElement(cloneElement(secTok.getToken()));
                }
            } */
            } else if (token instanceof SamlToken) {
                addSamlToken((SamlToken)token);
            } else {
                //REVISIT - not supported for signed.  Exception?
            }
        }
        
    }
    
    /**
     * Handle the endorsing supporting tokens
     */
    private void handleEndorsingSupportingTokens(AssertionInfoMap aim) throws Exception {
        Collection<AssertionInfo> ais;
        
        ais = getAllAssertionsByLocalname(aim, SPConstants.SIGNED_ENDORSING_SUPPORTING_TOKENS);
        if (!ais.isEmpty()) {
            SupportingTokens sgndSuppTokens = null;
            for (AssertionInfo ai : ais) {
                sgndSuppTokens = (SupportingTokens)ai.getAssertion();
                ai.setAsserted(true);
            }
            if (sgndSuppTokens != null) {
                for (AbstractToken token : sgndSuppTokens.getTokens()) {
                    handleEndorsingToken(token, sgndSuppTokens);
                }
            }
        }
        
        ais = getAllAssertionsByLocalname(aim, SPConstants.ENDORSING_SUPPORTING_TOKENS);
        if (!ais.isEmpty()) {
            SupportingTokens endSuppTokens = null;
            for (AssertionInfo ai : ais) {
                endSuppTokens = (SupportingTokens)ai.getAssertion();
                ai.setAsserted(true);
            } 
            
            if (endSuppTokens != null) {
                for (AbstractToken token : endSuppTokens.getTokens()) {
                    handleEndorsingToken(token, endSuppTokens);
                }
            }
        }
        ais = getAllAssertionsByLocalname(aim, SPConstants.ENDORSING_ENCRYPTED_SUPPORTING_TOKENS);
        if (!ais.isEmpty()) {
            SupportingTokens endSuppTokens = null;
            for (AssertionInfo ai : ais) {
                endSuppTokens = (SupportingTokens)ai.getAssertion();
                ai.setAsserted(true);
            } 
            
            if (endSuppTokens != null) {
                for (AbstractToken token : endSuppTokens.getTokens()) {
                    handleEndorsingToken(token, endSuppTokens);
                }
            }
        }
        ais = getAllAssertionsByLocalname(aim, SPConstants.SIGNED_ENDORSING_ENCRYPTED_SUPPORTING_TOKENS);
        if (!ais.isEmpty()) {
            SupportingTokens endSuppTokens = null;
            for (AssertionInfo ai : ais) {
                endSuppTokens = (SupportingTokens)ai.getAssertion();
                ai.setAsserted(true);
            } 
            
            if (endSuppTokens != null) {
                for (AbstractToken token : endSuppTokens.getTokens()) {
                    handleEndorsingToken(token, endSuppTokens);
                }
            }
        }
    }
    
    private void handleEndorsingToken(
        AbstractToken token, SupportingTokens wrapper
    ) throws Exception {
        /* TODO if (token instanceof IssuedToken
            || token instanceof SecureConversationToken
            || token instanceof SecurityContextToken
            || token instanceof KerberosToken
            || token instanceof SpnegoContextToken) {
            addSig(doIssuedTokenSignature(token, wrapper));
        } else */ 
        if (token instanceof X509Token
            || token instanceof KeyValueToken) {
            doX509TokenSignature(token, wrapper);
        } /* TODO else if (token instanceof SamlToken) {
            SamlAssertionWrapper assertionWrapper = addSamlToken((SamlToken)token);
            assertionWrapper.toDOM(saaj.getSOAPPart());
            storeAssertionAsSecurityToken(assertionWrapper);
            addSig(doIssuedTokenSignature(token, wrapper));
        } TODO else if (token instanceof UsernameToken) {
            // Create a UsernameToken object for derived keys and store the security token
            WSSecUsernameToken usernameToken = addDKUsernameToken((UsernameToken)token, true);
            String id = usernameToken.getId();
            byte[] secret = usernameToken.getDerivedKey();

            Date created = new Date();
            Date expires = new Date();
            expires.setTime(created.getTime() + 300000);
            SecurityToken tempTok = 
                new SecurityToken(id, usernameToken.getUsernameTokenElement(), created, expires);
            tempTok.setSecret(secret);
            getTokenStore().add(tempTok);
            message.setContextualProperty(SecurityConstants.TOKEN_ID, tempTok.getId());
            
            addSig(doIssuedTokenSignature(token, wrapper));
        }*/
    }
    
    private void doX509TokenSignature(AbstractToken token, SupportingTokens wrapper) 
        throws Exception {
        
        signPartsAndElements(wrapper.getSignedParts(), wrapper.getSignedElements());
        
        // Action
        Map<String, Object> config = getProperties();
        if (config.containsKey(ConfigurationConstants.ACTION)) {
            String action = (String)config.get(ConfigurationConstants.ACTION);
            config.put(ConfigurationConstants.ACTION, 
                       action + " " + ConfigurationConstants.SIGNATURE);
        } else {
            config.put(ConfigurationConstants.ACTION, 
                       ConfigurationConstants.SIGNATURE);
        }
        
        /*TODO if (token.getDerivedKeys() == DerivedKeys.RequireDerivedKeys) {
            WSSecEncryptedKey encrKey = getEncryptedKeyBuilder(wrapper, token);
            
            Element bstElem = encrKey.getBinarySecurityTokenElement();
            if (bstElem != null) {
                addTopDownElement(bstElem);
            }
            encrKey.appendToHeader(secHeader);
            
            WSSecDKSign dkSig = new WSSecDKSign(wssConfig);
            
            dkSig.setSigCanonicalization(binding.getAlgorithmSuite().getC14n().getValue());
            dkSig.setSignatureAlgorithm(binding.getAlgorithmSuite().getSymmetricSignature());
            AlgorithmSuiteType algType = binding.getAlgorithmSuite().getAlgorithmSuiteType();
            dkSig.setDerivedKeyLength(algType.getSignatureDerivedKeyLength() / 8);
            
            dkSig.setExternalKey(encrKey.getEphemeralKey(), encrKey.getId());
            
            dkSig.prepare(doc, secHeader);
            
            dkSig.setParts(sigParts);
            List<Reference> referenceList = dkSig.addReferencesToSign(sigParts, secHeader);
            
            //Do signature
            dkSig.appendDKElementToHeader(secHeader);
            dkSig.computeSignature(referenceList, false, null);
            
            return dkSig.getSignatureValue();
        } else {*/
        configureSignature(wrapper, token, false);
        // }
    }
    
    /**
     * Identifies the portions of the message to be signed/encrypted.
     */
    private void signPartsAndElements(
        SignedParts signedParts,
        SignedElements signedElements
    ) throws SOAPException {
        Map<String, Object> properties = getProperties();
        String parts = "";
        if (properties.containsKey(ConfigurationConstants.SIGNATURE_PARTS)) {
            parts = (String)properties.get(ConfigurationConstants.SIGNATURE_PARTS);
            if (!parts.endsWith(";")) {
                parts += ";";
            }
        }
        
        // Add timestamp
        if (timestampAdded) {
            parts += "{Element}{" + WSSConstants.NS_WSU10 + "}Timestamp;";
        }

        // Add SignedParts
        if (signedParts != null) {
            if (signedParts.isBody()) {
                parts += "{Element}{" + WSSConstants.NS_SOAP11 + "}Body;";
            }
            
            for (Header head : signedParts.getHeaders()) {
                parts += "{Element}{" +  head.getNamespace() + "}" + head.getName() + ";";
            }
        }
        /*
         * TODO
        if (signedElements != null) {
            // Handle SignedElements
            try {
                result.addAll(
                    this.getElements(
                        "Element", signedElements.getXPaths(), found, true
                    )
                );
            } catch (XPathExpressionException e) {
                LOG.log(Level.FINE, e.getMessage(), e);
                // REVISIT
            }
        }
        */
        
        properties.put(ConfigurationConstants.SIGNATURE_PARTS, parts);
    }


}
