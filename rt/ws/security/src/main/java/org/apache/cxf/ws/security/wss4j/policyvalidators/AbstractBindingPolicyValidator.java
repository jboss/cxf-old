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

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.ws.policy.AssertionInfo;
import org.apache.cxf.ws.policy.AssertionInfoMap;
import org.apache.neethi.Assertion;

import org.apache.wss4j.common.saml.SAMLKeyInfo;
import org.apache.wss4j.common.saml.SamlAssertionWrapper;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.WSDataRef;
import org.apache.wss4j.dom.WSSecurityEngineResult;
import org.apache.wss4j.dom.message.token.BinarySecurity;
import org.apache.wss4j.dom.message.token.PKIPathSecurity;
import org.apache.wss4j.dom.message.token.Timestamp;
import org.apache.wss4j.dom.message.token.X509Security;
import org.apache.wss4j.dom.util.WSSecurityUtil;
import org.apache.wss4j.policy.SP11Constants;
import org.apache.wss4j.policy.SP12Constants;
import org.apache.wss4j.policy.SPConstants;
import org.apache.wss4j.policy.model.AbstractSymmetricAsymmetricBinding;
import org.apache.wss4j.policy.model.AbstractSymmetricAsymmetricBinding.ProtectionOrder;
import org.apache.wss4j.policy.model.AbstractToken;
import org.apache.wss4j.policy.model.AbstractToken.DerivedKeys;
import org.apache.wss4j.policy.model.AbstractTokenWrapper;
import org.apache.wss4j.policy.model.EncryptionToken;
import org.apache.wss4j.policy.model.Layout;
import org.apache.wss4j.policy.model.Layout.LayoutType;
import org.apache.wss4j.policy.model.ProtectionToken;
import org.apache.wss4j.policy.model.SignatureToken;
import org.apache.wss4j.policy.model.X509Token;

/**
 * Some abstract functionality for validating a security binding.
 */
public abstract class AbstractBindingPolicyValidator implements BindingPolicyValidator {
    
    private static final QName SIG_QNAME = new QName(WSConstants.SIG_NS, WSConstants.SIG_LN);
    
    /**
     * Validate a Timestamp
     * @param includeTimestamp whether a Timestamp must be included or not
     * @param transportBinding whether the Transport binding is in use or not
     * @param signedResults the signed results list
     * @param message the Message object
     * @return whether the Timestamp policy is valid or not
     */
    protected boolean validateTimestamp(
        boolean includeTimestamp,
        boolean transportBinding,
        List<WSSecurityEngineResult> results,
        List<WSSecurityEngineResult> signedResults,
        Message message
    ) {
        List<WSSecurityEngineResult> timestampResults = 
            WSSecurityUtil.fetchAllActionResults(results, WSConstants.TS);
        
        // Check whether we received a timestamp and compare it to the policy
        if (includeTimestamp && timestampResults.size() != 1) {
            return false;
        } else if (!includeTimestamp) {
            if (timestampResults.isEmpty()) {
                return true;
            }
            return false;
        }
        
        // At this point we received a (required) Timestamp. Now check that it is integrity protected.
        if (transportBinding) {
            return true;
        } else if (!signedResults.isEmpty()) {
            Timestamp timestamp = 
                (Timestamp)timestampResults.get(0).get(WSSecurityEngineResult.TAG_TIMESTAMP);
            for (WSSecurityEngineResult signedResult : signedResults) {
                List<WSDataRef> dataRefs = 
                    CastUtils.cast((List<?>)signedResult.get(WSSecurityEngineResult.TAG_DATA_REF_URIS));
                for (WSDataRef dataRef : dataRefs) {
                    if (timestamp.getElement() == dataRef.getProtectedElement()) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Validate the entire header and body signature property.
     */
    protected boolean validateEntireHeaderAndBodySignatures(
        List<WSSecurityEngineResult> signedResults
    ) {
        for (WSSecurityEngineResult signedResult : signedResults) {
            List<WSDataRef> dataRefs = 
                    CastUtils.cast((List<?>)signedResult.get(WSSecurityEngineResult.TAG_DATA_REF_URIS));
            for (WSDataRef dataRef : dataRefs) {
                String xpath = dataRef.getXpath();
                if (xpath != null) {
                    String[] nodes = StringUtils.split(xpath, "/");
                    // envelope/Body || envelope/Header/header || envelope/Header/wsse:Security/header
                    if (nodes.length < 3 || nodes.length > 5) {
                        return false;
                    }
                    
                    if (!(nodes[2].contains("Header") || nodes[2].contains("Body"))) {
                        return false;
                    }
                    
                    if (nodes.length == 5 && !nodes[3].contains("Security")) {
                        return false;
                    }
                    
                    if (nodes.length == 4 && nodes[2].contains("Body")) {
                        return false;
                    }
                    
                }
            }
        }
        return true;
    }
    
    /**
     * Validate the layout assertion. It just checks the LaxTsFirst and LaxTsLast properties
     */
    protected boolean validateLayout(
        boolean laxTimestampFirst,
        boolean laxTimestampLast,
        List<WSSecurityEngineResult> results
    ) {
        if (laxTimestampFirst) {
            if (results.isEmpty()) {
                return false;
            }
            Integer firstAction = (Integer)results.get(results.size() - 1).get(WSSecurityEngineResult.TAG_ACTION);
            if (firstAction.intValue() != WSConstants.TS) {
                return false;
            }
        } else if (laxTimestampLast) {
            if (results.isEmpty()) {
                return false;
            }
            Integer lastAction = 
                (Integer)results.get(0).get(WSSecurityEngineResult.TAG_ACTION);
            if (lastAction.intValue() != WSConstants.TS) {
                return false;
            }
        }
        return true;
        
    }
    
    /**
     * Check various properties set in the policy of the binding
     */
    protected boolean checkProperties(
        AbstractSymmetricAsymmetricBinding binding, 
        AssertionInfo ai,
        AssertionInfoMap aim,
        List<WSSecurityEngineResult> results,
        List<WSSecurityEngineResult> signedResults,
        Message message
    ) {
        // Check the AlgorithmSuite
        AlgorithmSuitePolicyValidator algorithmValidator = new AlgorithmSuitePolicyValidator(results);
        if (!algorithmValidator.validatePolicy(ai, binding.getAlgorithmSuite())) {
            return false;
        }
        assertPolicy(aim, binding.getAlgorithmSuite());
        String namespace = binding.getAlgorithmSuite().getAlgorithmSuiteType().getNamespace();
        String name = binding.getAlgorithmSuite().getAlgorithmSuiteType().getName();
        Collection<AssertionInfo> algSuiteAis = aim.get(new QName(namespace, name));
        if (algSuiteAis != null) {
            for (AssertionInfo algSuiteAi : algSuiteAis) {
                algSuiteAi.setAsserted(true);
            }
        }
        
        // Check the IncludeTimestamp
        if (!validateTimestamp(binding.isIncludeTimestamp(), false, results, signedResults, message)) {
            String error = "Received Timestamp does not match the requirements";
            ai.setNotAsserted(error);
            return false;
        }
        assertPolicy(aim, SPConstants.INCLUDE_TIMESTAMP);
        
        // Check the Layout
        Layout layout = binding.getLayout();
        LayoutType layoutType = layout.getLayoutType();
        boolean timestampFirst = layoutType == LayoutType.LaxTsFirst;
        boolean timestampLast = layoutType == LayoutType.LaxTsLast;
        if (!validateLayout(timestampFirst, timestampLast, results)) {
            String error = "Layout does not match the requirements";
            notAssertPolicy(aim, layout, error);
            ai.setNotAsserted(error);
            return false;
        }
        assertPolicy(aim, layout);
        assertPolicy(aim, SPConstants.LAYOUT_LAX);
        assertPolicy(aim, SPConstants.LAYOUT_LAX_TIMESTAMP_FIRST);
        assertPolicy(aim, SPConstants.LAYOUT_LAX_TIMESTAMP_LAST);
        assertPolicy(aim, SPConstants.LAYOUT_STRICT);
        
        // Check the EntireHeaderAndBodySignatures property
        if (binding.isOnlySignEntireHeadersAndBody()
            && !validateEntireHeaderAndBodySignatures(signedResults)) {
            String error = "OnlySignEntireHeadersAndBody does not match the requirements";
            ai.setNotAsserted(error);
            return false;
        }
        assertPolicy(aim, SPConstants.ONLY_SIGN_ENTIRE_HEADERS_AND_BODY);
        
        // Check whether the signatures were encrypted or not
        if (binding.isEncryptSignature() && !isSignatureEncrypted(results)) {
            ai.setNotAsserted("The signature is not protected");
            return false;
        }
        assertPolicy(aim, SPConstants.ENCRYPT_SIGNATURE);
        assertPolicy(aim, SPConstants.PROTECT_TOKENS);
        
        /*
        // Check ProtectTokens
        if (binding.isTokenProtection() && !isTokenProtected(results, signedResults)) {
            ai.setNotAsserted("The token protection property is not valid");
            return false;
        }
        */
        
        return true;
    }
    
    /**
     * Check the Protection Order of the binding
     */
    protected boolean checkProtectionOrder(
        AbstractSymmetricAsymmetricBinding binding, 
        AssertionInfoMap aim,
        AssertionInfo ai,
        List<WSSecurityEngineResult> results
    ) {
        ProtectionOrder protectionOrder = binding.getProtectionOrder();
        if (protectionOrder == ProtectionOrder.EncryptBeforeSigning) {
            if (!binding.isProtectTokens() && isSignedBeforeEncrypted(results)) {
                ai.setNotAsserted("Not encrypted before signed");
                return false;
            }
            assertPolicy(aim, SPConstants.ENCRYPT_BEFORE_SIGNING);
        } else if (protectionOrder == ProtectionOrder.SignBeforeEncrypting) { 
            if (isEncryptedBeforeSigned(results)) {
                ai.setNotAsserted("Not signed before encrypted");
                return false;
            }
            assertPolicy(aim, SPConstants.SIGN_BEFORE_ENCRYPTING);
        }
        return true;
    }
    
    /**
     * Check to see if a signature was applied before encryption.
     * Note that results are stored in the reverse order.
     */
    private boolean isSignedBeforeEncrypted(List<WSSecurityEngineResult> results) {
        boolean signed = false;
        for (WSSecurityEngineResult result : results) {
            Integer actInt = (Integer)result.get(WSSecurityEngineResult.TAG_ACTION);
            List<WSDataRef> el = 
                CastUtils.cast((List<?>)result.get(WSSecurityEngineResult.TAG_DATA_REF_URIS));
            
            // Don't count an endorsing signature
            if (actInt.intValue() == WSConstants.SIGN && el != null
                && !(el.size() == 1 && el.get(0).getName().equals(SIG_QNAME))) {
                signed = true;
            }
            if (actInt.intValue() == WSConstants.ENCR && el != null) {
                if (signed) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }
    
    /**
     * Check to see if encryption was applied before signature.
     * Note that results are stored in the reverse order.
     */
    private boolean isEncryptedBeforeSigned(List<WSSecurityEngineResult> results) {
        boolean encrypted = false;
        for (WSSecurityEngineResult result : results) {
            Integer actInt = (Integer)result.get(WSSecurityEngineResult.TAG_ACTION);
            List<WSDataRef> el = 
                CastUtils.cast((List<?>)result.get(WSSecurityEngineResult.TAG_DATA_REF_URIS));
            
            if (actInt.intValue() == WSConstants.ENCR && el != null) {
                encrypted = true;
            }
            // Don't count an endorsing signature
            if (actInt.intValue() == WSConstants.SIGN && el != null
                && !(el.size() == 1 && el.get(0).getName().equals(SIG_QNAME))) {
                if (encrypted) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }
    
    /**
     * Check the derived key requirement.
     */
    protected boolean checkDerivedKeys(
        AbstractTokenWrapper tokenWrapper, 
        boolean hasDerivedKeys,
        List<WSSecurityEngineResult> signedResults,
        List<WSSecurityEngineResult> encryptedResults
    ) {
        AbstractToken token = tokenWrapper.getToken();
        boolean isDerivedKeys = token.getDerivedKeys() == DerivedKeys.RequireDerivedKeys;
        // If derived keys are not required then just return
        if (!(token instanceof X509Token && isDerivedKeys)) {
            return true;
        }
        if (tokenWrapper instanceof EncryptionToken 
            && !hasDerivedKeys && !encryptedResults.isEmpty()) {
            return false;
        } else if (tokenWrapper instanceof SignatureToken
            && !hasDerivedKeys && !signedResults.isEmpty()) {
            return false;
        } else if (tokenWrapper instanceof ProtectionToken
            && !hasDerivedKeys && !(signedResults.isEmpty() || encryptedResults.isEmpty())) {
            return false;
        }
        return true;
    }
    
    /**
     * Check whether the token protection policy is followed. In other words, check that the
     * signature token was itself signed.
     */
    protected boolean isTokenProtected(
        List<WSSecurityEngineResult> results,
        List<WSSecurityEngineResult> signedResults
    ) {
        for (int i = 0; i < signedResults.size(); i++) {
            WSSecurityEngineResult result = signedResults.get(i);
            
            // Get the Token result that was used for the signature
            WSSecurityEngineResult tokenResult = 
                findCorrespondingToken(result, results);
            if (tokenResult == null) {
                return false;
            }
            
            // Now go through what was signed and see if the token itself was signed
            List<WSDataRef> sl =
                CastUtils.cast((List<?>)result.get(
                    WSSecurityEngineResult.TAG_DATA_REF_URIS
                ));
            boolean found = false;
            if (sl != null) {
                for (WSDataRef dataRef : sl) {
                    Element referenceElement = dataRef.getProtectedElement();
                    if (referenceElement != null
                        && referenceElement.equals(tokenResult.get(WSSecurityEngineResult.TAG_TOKEN_ELEMENT))) {
                        found = true;
                    }
                }
            }
            if (!found) {
                return false;
            }

        }
        return true;
    }
    
    /**
     * Find the token corresponding to either the X509Certificate or PublicKey used to sign
     * the "signatureResult" argument.
     */
    private WSSecurityEngineResult findCorrespondingToken(
        WSSecurityEngineResult signatureResult,
        List<WSSecurityEngineResult> results
    ) {
        // See what was used to sign this result
        X509Certificate cert = 
            (X509Certificate)signatureResult.get(WSSecurityEngineResult.TAG_X509_CERTIFICATE);
        PublicKey publicKey = 
            (PublicKey)signatureResult.get(WSSecurityEngineResult.TAG_PUBLIC_KEY);
        
        for (WSSecurityEngineResult token : results) {
            Integer actInt = (Integer)token.get(WSSecurityEngineResult.TAG_ACTION);
            if (actInt == WSConstants.SIGN) {
                continue;
            }
            
            BinarySecurity binarySecurity = 
                (BinarySecurity)token.get(WSSecurityEngineResult.TAG_BINARY_SECURITY_TOKEN);
            PublicKey foundPublicKey = 
                (PublicKey)token.get(WSSecurityEngineResult.TAG_PUBLIC_KEY);
            if (binarySecurity instanceof X509Security
                || binarySecurity instanceof PKIPathSecurity) {
                X509Certificate foundCert = 
                    (X509Certificate)token.get(WSSecurityEngineResult.TAG_X509_CERTIFICATE);
                if (foundCert.equals(cert)) {
                    return token;
                }
            } else if (actInt.intValue() == WSConstants.ST_SIGNED
                || actInt.intValue() == WSConstants.ST_UNSIGNED) {
                SamlAssertionWrapper assertionWrapper = 
                    (SamlAssertionWrapper)token.get(WSSecurityEngineResult.TAG_SAML_ASSERTION);
                SAMLKeyInfo samlKeyInfo = assertionWrapper.getSubjectKeyInfo();
                if (samlKeyInfo != null) {
                    X509Certificate[] subjectCerts = samlKeyInfo.getCerts();
                    PublicKey subjectPublicKey = samlKeyInfo.getPublicKey();
                    if ((cert != null && subjectCerts != null 
                        && cert.equals(subjectCerts[0]))
                        || (subjectPublicKey != null && subjectPublicKey.equals(publicKey))) {
                        return token;
                    }
                }
            } else if (publicKey != null && publicKey.equals(foundPublicKey)) {
                return token;
            } 
        }
        return null;
    }
    
    /**
     * Check whether the primary Signature (and all SignatureConfirmation) elements were encrypted
     */
    protected boolean isSignatureEncrypted(List<WSSecurityEngineResult> results) {
        boolean foundPrimarySignature = false;
        for (int i = results.size() - 1; i >= 0; i--) {
            WSSecurityEngineResult result = results.get(i);
            Integer actInt = (Integer)result.get(WSSecurityEngineResult.TAG_ACTION);
            if (actInt.intValue() == WSConstants.SIGN && !foundPrimarySignature) {
                foundPrimarySignature = true;
                String sigId = (String)result.get(WSSecurityEngineResult.TAG_ID);
                if (sigId == null || !isIdEncrypted(sigId, results)) {
                    return false;
                }
            } else if (actInt.intValue() == WSConstants.SC) {
                String sigId = (String)result.get(WSSecurityEngineResult.TAG_ID);
                if (sigId == null || !isIdEncrypted(sigId, results)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Return true if the given id was encrypted
     */
    private boolean isIdEncrypted(String sigId, List<WSSecurityEngineResult> results) {
        for (WSSecurityEngineResult wser : results) {
            Integer actInt = (Integer)wser.get(WSSecurityEngineResult.TAG_ACTION);
            if (actInt.intValue() == WSConstants.ENCR) {
                List<WSDataRef> el = 
                    CastUtils.cast((List<?>)wser.get(WSSecurityEngineResult.TAG_DATA_REF_URIS));
                if (el != null) {
                    for (WSDataRef r : el) {
                        Element protectedElement = r.getProtectedElement();
                        if (protectedElement != null) {
                            String id = protectedElement.getAttributeNS(null, "Id");
                            String wsuId = protectedElement.getAttributeNS(WSConstants.WSU_NS, "Id");
                            if (sigId.equals(id) || sigId.equals(wsuId)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
    
    protected void assertPolicy(AssertionInfoMap aim, Assertion token) {
        Collection<AssertionInfo> ais = aim.get(token.getName());
        if (ais != null && !ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                if (ai.getAssertion() == token) {
                    ai.setAsserted(true);
                }
            }    
        }
    }
    
    protected void notAssertPolicy(AssertionInfoMap aim, Assertion token, String msg) {
        Collection<AssertionInfo> ais = aim.get(token.getName());
        if (ais != null && !ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                if (ai.getAssertion() == token) {
                    ai.setNotAsserted(msg);
                }
            }    
        }
    }
    
    protected boolean assertPolicy(AssertionInfoMap aim, String localname) {
        Collection<AssertionInfo> ais = getAllAssertionsByLocalname(aim, localname);
        if (!ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                ai.setAsserted(true);
            }    
            return true;
        }
        return false;
    }
    
    protected boolean assertPolicy(AssertionInfoMap aim, QName q) {
        Collection<AssertionInfo> ais = aim.get(q);
        if (ais != null && !ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                ai.setAsserted(true);
            }    
            return true;
        }
        return false;
    }
    
    protected void notAssertPolicy(AssertionInfoMap aim, QName q, String msg) {
        Collection<AssertionInfo> ais = aim.get(q);
        if (ais != null && !ais.isEmpty()) {
            for (AssertionInfo ai : ais) {
                ai.setNotAsserted(msg);
            }    
        }
    }
    
    protected Collection<AssertionInfo> getAllAssertionsByLocalname(
        AssertionInfoMap aim,
        String localname
    ) {
        Collection<AssertionInfo> sp11Ais = aim.get(new QName(SP11Constants.SP_NS, localname));
        Collection<AssertionInfo> sp12Ais = aim.get(new QName(SP12Constants.SP_NS, localname));
        
        if ((sp11Ais != null && !sp11Ais.isEmpty()) || (sp12Ais != null && !sp12Ais.isEmpty())) {
            Collection<AssertionInfo> ais = new HashSet<AssertionInfo>();
            if (sp11Ais != null) {
                ais.addAll(sp11Ais);
            }
            if (sp12Ais != null) {
                ais.addAll(sp12Ais);
            }
            return ais;
        }
            
        return Collections.emptySet();
    }
}
