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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.xml.crypto.dsig.Reference;
import javax.xml.soap.SOAPMessage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.ws.policy.AssertionInfo;
import org.apache.cxf.ws.policy.AssertionInfoMap;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.apache.cxf.ws.security.tokenstore.TokenStore;
import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.derivedKey.ConversationConstants;
import org.apache.wss4j.common.derivedKey.ConversationException;
import org.apache.wss4j.common.ext.WSSecurityException;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.WSEncryptionPart;
import org.apache.wss4j.dom.WSSConfig;
import org.apache.wss4j.dom.WSSecurityEngineResult;
import org.apache.wss4j.dom.bsp.BSPEnforcer;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.apache.wss4j.dom.handler.WSHandlerResult;
import org.apache.wss4j.dom.message.WSSecBase;
import org.apache.wss4j.dom.message.WSSecDKEncrypt;
import org.apache.wss4j.dom.message.WSSecDKSign;
import org.apache.wss4j.dom.message.WSSecEncrypt;
import org.apache.wss4j.dom.message.WSSecEncryptedKey;
import org.apache.wss4j.dom.message.WSSecHeader;
import org.apache.wss4j.dom.message.WSSecSignature;
import org.apache.wss4j.dom.message.WSSecTimestamp;
import org.apache.wss4j.dom.message.WSSecUsernameToken;
import org.apache.wss4j.dom.message.token.SecurityTokenReference;
import org.apache.wss4j.dom.util.WSSecurityUtil;
import org.apache.wss4j.policy.SP11Constants;
import org.apache.wss4j.policy.SP12Constants;
import org.apache.wss4j.policy.SPConstants;
import org.apache.wss4j.policy.model.AbstractSymmetricAsymmetricBinding;
import org.apache.wss4j.policy.model.AbstractToken;
import org.apache.wss4j.policy.model.AbstractToken.DerivedKeys;
import org.apache.wss4j.policy.model.AbstractTokenWrapper;
import org.apache.wss4j.policy.model.AlgorithmSuite;
import org.apache.wss4j.policy.model.AlgorithmSuite.AlgorithmSuiteType;
import org.apache.wss4j.policy.model.IssuedToken;
import org.apache.wss4j.policy.model.KerberosToken;
import org.apache.wss4j.policy.model.SecureConversationToken;
import org.apache.wss4j.policy.model.SecurityContextToken;
import org.apache.wss4j.policy.model.SpnegoContextToken;
import org.apache.wss4j.policy.model.SymmetricBinding;
import org.apache.wss4j.policy.model.UsernameToken;
import org.apache.wss4j.policy.model.X509Token;
import org.apache.xml.security.utils.Base64;

/**
 * 
 */
public class SymmetricBindingHandler extends AbstractBindingBuilder {
    SymmetricBinding sbinding;
    TokenStore tokenStore;
    
    public SymmetricBindingHandler(WSSConfig config, 
                                   SymmetricBinding binding,
                                    SOAPMessage saaj,
                                    WSSecHeader secHeader,
                                    AssertionInfoMap aim,
                                    SoapMessage message) {
        super(config, binding, saaj, secHeader, aim, message);
        this.sbinding = binding;
        tokenStore = getTokenStore();
        protectionOrder = binding.getProtectionOrder();
    }
    
    private AbstractTokenWrapper getSignatureToken() {
        if (sbinding.getProtectionToken() != null) {
            return sbinding.getProtectionToken();
        }
        return sbinding.getSignatureToken();
    }
    
    private AbstractTokenWrapper getEncryptionToken() {
        if (sbinding.getProtectionToken() != null) {
            return sbinding.getProtectionToken();
        }
        return sbinding.getEncryptionToken();
    }
    
    public void handleBinding() {
        WSSecTimestamp timestamp = createTimestamp();
        handleLayout(timestamp);
        
        if (isRequestor()) {
            //Setup required tokens
            initializeTokens();
        }
        
        if (sbinding.getProtectionOrder() 
            == AbstractSymmetricAsymmetricBinding.ProtectionOrder.EncryptBeforeSigning) {
            doEncryptBeforeSign();
        } else {
            doSignBeforeEncrypt();
        }
        //REVIST - what to do with these policies?
        policyAsserted(SP11Constants.TRUST_10);
        policyAsserted(SP12Constants.TRUST_13);
    }
    
    private void initializeTokens()  {
        //Setting up encryption token and signature token
        /*
        Token sigTok = getSignatureToken().getToken();
        //Token encrTok = getEncryptionToken().getToken();
        
        if (sigTok instanceof IssuedToken) {
            //IssuedToken issuedToken = (IssuedToken)sigTok;
            
            //REVISIT - WS-Trust STS token retrieval
        } else if (sigTok instanceof SecureConversationToken) {
            //REVISIT - SecureConversation token retrieval
        }
        */
    }
    
    private void doEncryptBeforeSign() {
        try {
            AbstractTokenWrapper encryptionWrapper = getEncryptionToken();
            AbstractToken encryptionToken = encryptionWrapper.getToken();
            List<WSEncryptionPart> encrParts = getEncryptedParts();
            List<WSEncryptionPart> sigParts = getSignedParts();
            
            //if (encryptionToken == null && encrParts.size() > 0) {
                //REVISIT - nothing to encrypt?
            //}
            
            if (encryptionToken != null && encrParts.size() > 0) {
                //The encryption token can be an IssuedToken or a 
                //SecureConversationToken
                String tokenId = null;
                SecurityToken tok = null;
                if (encryptionToken instanceof IssuedToken 
                    || encryptionToken instanceof KerberosToken
                    || encryptionToken instanceof SecureConversationToken
                    || encryptionToken instanceof SecurityContextToken
                    || encryptionToken instanceof SpnegoContextToken) {
                    tok = getSecurityToken();
                } else if (encryptionToken instanceof X509Token) {
                    if (isRequestor()) {
                        tokenId = setupEncryptedKey(encryptionWrapper, encryptionToken);
                    } else {
                        tokenId = getEncryptedKey();
                    }
                } else if (encryptionToken instanceof UsernameToken) {
                    if (isRequestor()) {
                        tokenId = setupUTDerivedKey((UsernameToken)encryptionToken);
                    } else {
                        tokenId = getUTDerivedKey();
                    }
                }
                if (tok == null) {
                    //if (tokenId == null || tokenId.length() == 0) {
                        //REVISIT - no tokenId?   Exception?
                    //}
                    if (tokenId != null && tokenId.startsWith("#")) {
                        tokenId = tokenId.substring(1);
                    }
                    
                    /*
                     * Get hold of the token from the token storage
                     */
                    tok = tokenStore.getToken(tokenId);
                }
    
                boolean attached = false;
                
                if (includeToken(encryptionToken.getIncludeTokenType())) {
                    Element el = tok.getToken();
                    this.addEncryptedKeyElement(cloneElement(el));
                    attached = true;
                } else if (encryptionToken instanceof X509Token && isRequestor()) {
                    Element el = tok.getToken();
                    this.addEncryptedKeyElement(cloneElement(el));
                    attached = true;
                }
                
                WSSecBase encr = doEncryption(encryptionWrapper, tok, attached, encrParts, true);
                
                handleEncryptedSignedHeaders(encrParts, sigParts);
                
                if (timestampEl != null) {
                    WSEncryptionPart timestampPart = 
                        convertToEncryptionPart(timestampEl.getElement());
                    sigParts.add(timestampPart);        
                }
                
                if (isRequestor()) {
                    this.addSupportingTokens(sigParts);
                } else {
                    addSignatureConfirmation(sigParts);
                }
                
                //Sign the message
                //We should use the same key in the case of EncryptBeforeSig
                if (sigParts.size() > 0) {
                    signatures.add(this.doSignature(sigParts, encryptionWrapper, encryptionToken, 
                                                    tok, attached));
                }
                
                if (isRequestor()) {
                    this.doEndorse();
                }
                
                //Check for signature protection and encryption of UsernameToken
                if (sbinding.isEncryptSignature() 
                    || encryptedTokensList.size() > 0 && isRequestor()) {
                    List<WSEncryptionPart> secondEncrParts = new ArrayList<WSEncryptionPart>();
                    
                    //Now encrypt the signature using the above token
                    if (sbinding.isEncryptSignature()) {
                        if (this.mainSigId != null) {
                            WSEncryptionPart sigPart = 
                                new WSEncryptionPart(this.mainSigId, "Element");
                            sigPart.setElement(bottomUpElement);
                            secondEncrParts.add(sigPart);
                        }
                        if (sigConfList != null && !sigConfList.isEmpty()) {
                            secondEncrParts.addAll(sigConfList);
                        }
                    }
                    
                    if (isRequestor()) {
                        secondEncrParts.addAll(encryptedTokensList);
                    }
                    
                    Element secondRefList = null;
                    
                    if (encryptionToken.getDerivedKeys() == DerivedKeys.RequireDerivedKeys 
                        && !secondEncrParts.isEmpty()) {
                        secondRefList = ((WSSecDKEncrypt)encr).encryptForExternalRef(null, 
                                secondEncrParts);
                        this.addDerivedKeyElement(secondRefList);
                    } else if (!secondEncrParts.isEmpty()) {
                        //Encrypt, get hold of the ref list and add it
                        secondRefList = ((WSSecEncrypt)encr).encryptForRef(null, encrParts);
                        this.addDerivedKeyElement(secondRefList);
                    }
                }
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new Fault(ex);
        }
    }
    
    private void doSignBeforeEncrypt() {
        AbstractTokenWrapper sigAbstractTokenWrapper = getSignatureToken();
        AbstractToken sigToken = sigAbstractTokenWrapper.getToken();
        
        String sigTokId = null;
        Element sigTokElem = null;
        
        try {
            SecurityToken sigTok = null;
            if (sigToken != null) {
                if (sigToken instanceof SecureConversationToken
                    || sigToken instanceof SecurityContextToken
                    || sigToken instanceof IssuedToken 
                    || sigToken instanceof KerberosToken
                    || sigToken instanceof SpnegoContextToken) {
                    sigTok = getSecurityToken();
                } else if (sigToken instanceof X509Token) {
                    if (isRequestor()) {
                        sigTokId = setupEncryptedKey(sigAbstractTokenWrapper, sigToken);
                    } else {
                        sigTokId = getEncryptedKey();
                    }
                } else if (sigToken instanceof UsernameToken) {
                    if (isRequestor()) {
                        sigTokId = setupUTDerivedKey((UsernameToken)sigToken);
                    } else {
                        sigTokId = getUTDerivedKey();
                    }
                }
            } else {
                policyNotAsserted(sbinding, "No signature token");
                return;
            }
            
            if (sigTok == null && StringUtils.isEmpty(sigTokId)) {
                policyNotAsserted(sigAbstractTokenWrapper, "No signature token id");
                return;
            } else {
                policyAsserted(sigAbstractTokenWrapper);
            }
            if (sigTok == null) {
                sigTok = tokenStore.getToken(sigTokId);
            }
            //if (sigTok == null) {
                //REVISIT - no token?
            //}
            
            boolean tokIncluded = true;
            if (includeToken(sigToken.getIncludeTokenType())) {
                Element el = sigTok.getToken();
                sigTokElem = cloneElement(el);
                this.addEncryptedKeyElement(sigTokElem);
            } else if (isRequestor() && sigToken instanceof X509Token) {
                Element el = sigTok.getToken();
                sigTokElem = cloneElement(el);
                this.addEncryptedKeyElement(sigTokElem);
            } else {
                tokIncluded = false;
            }
        
            //Add timestamp
            List<WSEncryptionPart> sigs = getSignedParts();
            if (timestampEl != null) {
                WSEncryptionPart timestampPart = convertToEncryptionPart(timestampEl.getElement());
                sigs.add(timestampPart);        
            }

            if (isRequestor()) {
                addSupportingTokens(sigs);
                if (!sigs.isEmpty()) {
                    signatures.add(doSignature(sigs, sigAbstractTokenWrapper, sigToken, sigTok, tokIncluded));
                }
                doEndorse();
            } else {
                //confirm sig
                assertSupportingTokens(sigs);
                addSignatureConfirmation(sigs);
                if (!sigs.isEmpty()) {
                    doSignature(sigs, sigAbstractTokenWrapper, sigToken, sigTok, tokIncluded);
                }
            }

            //Encryption
            AbstractTokenWrapper encrAbstractTokenWrapper = getEncryptionToken();
            AbstractToken encrToken = encrAbstractTokenWrapper.getToken();
            SecurityToken encrTok = null;
            if (sigToken.equals(encrToken)) {
                //Use the same token
                encrTok = sigTok;
            } else {
                policyNotAsserted(sbinding, "Encryption token does not equal signature token");
                return;
            }
            
            List<WSEncryptionPart> enc = getEncryptedParts();
            
            //Check for signature protection
            if (sbinding.isEncryptSignature()) {
                if (mainSigId != null) {
                    WSEncryptionPart sigPart = new WSEncryptionPart(mainSigId, "Element");
                    sigPart.setElement(bottomUpElement);
                    enc.add(sigPart);
                }
                if (sigConfList != null && !sigConfList.isEmpty()) {
                    enc.addAll(sigConfList);
                }
            }
            
            if (isRequestor()) {
                enc.addAll(encryptedTokensList);
            }
            doEncryption(encrAbstractTokenWrapper,
                         encrTok,
                         tokIncluded,
                         enc,
                         false);
        } catch (Exception e) {
            throw new Fault(e);
        }
    }
    
    private WSSecBase doEncryptionDerived(AbstractTokenWrapper recToken,
                                          SecurityToken encrTok,
                                          AbstractToken encrToken,
                                          boolean attached,
                                          List<WSEncryptionPart> encrParts,
                                          boolean atEnd) {
        try {
            WSSecDKEncrypt dkEncr = new WSSecDKEncrypt(wssConfig);
            if (recToken.getToken().getVersion() == SPConstants.SPVersion.SP12) {
                dkEncr.setWscVersion(ConversationConstants.VERSION_05_12);
            }

            if (attached && encrTok.getAttachedReference() != null) {
                dkEncr.setExternalKey(
                    encrTok.getSecret(), cloneElement(encrTok.getAttachedReference())
                );
            } else if (encrTok.getUnattachedReference() != null) {
                dkEncr.setExternalKey(
                    encrTok.getSecret(), cloneElement(encrTok.getUnattachedReference())
                );
            } else if (!isRequestor() && encrTok.getSHA1() != null) {
                // If the Encrypted key used to create the derived key is not
                // attached use key identifier as defined in WSS1.1 section
                // 7.7 Encrypted Key reference
                SecurityTokenReference tokenRef = new SecurityTokenReference(saaj.getSOAPPart());
                tokenRef.setKeyIdentifierEncKeySHA1(encrTok.getSHA1());
                String tokenType = encrTok.getTokenType();
                if (tokenType == null) {
                    tokenType = WSConstants.WSS_ENC_KEY_VALUE_TYPE;
                }
                tokenRef.addTokenType(tokenType);
                dkEncr.setExternalKey(encrTok.getSecret(), tokenRef.getElement());
            } else {
                if (attached) {
                    String id = encrTok.getWsuId();
                    if (id == null 
                        && (encrToken instanceof SecureConversationToken 
                            || encrToken instanceof SecurityContextToken)) {
                        dkEncr.setTokenIdDirectId(true);
                        id = encrTok.getId();
                    } else if (id == null) {
                        id = encrTok.getId();
                    }
                    if (id.startsWith("#")) {
                        id = id.substring(1);
                    }
                    dkEncr.setExternalKey(encrTok.getSecret(), id);
                } else {
                    dkEncr.setTokenIdDirectId(true);
                    dkEncr.setExternalKey(encrTok.getSecret(), encrTok.getId());
                }
            }
            
            if (encrTok.getSHA1() != null) {
                String tokenType = encrTok.getTokenType();
                if (tokenType == null) {
                    tokenType = WSConstants.WSS_ENC_KEY_VALUE_TYPE;
                }
                dkEncr.setCustomValueType(tokenType);
            } else {
                String tokenType = encrTok.getTokenType();
                if (WSConstants.WSS_SAML_TOKEN_TYPE.equals(tokenType)
                    || WSConstants.SAML_NS.equals(tokenType)) {
                    dkEncr.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                    dkEncr.setCustomValueType(WSConstants.WSS_SAML_KI_VALUE_TYPE);
                } else if (WSConstants.WSS_SAML2_TOKEN_TYPE.equals(tokenType)
                    || WSConstants.SAML2_NS.equals(tokenType)) {
                    dkEncr.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                    dkEncr.setCustomValueType(WSConstants.WSS_SAML2_KI_VALUE_TYPE);
                } else if (encrToken instanceof UsernameToken) {
                    dkEncr.setCustomValueType(WSConstants.WSS_USERNAME_TOKEN_VALUE_TYPE);
                } else {
                    dkEncr.setCustomValueType(tokenType);
                }
            }
            
            AlgorithmSuiteType algType = sbinding.getAlgorithmSuite().getAlgorithmSuiteType();
            dkEncr.setSymmetricEncAlgorithm(algType.getEncryption());
            dkEncr.setDerivedKeyLength(algType.getEncryptionDerivedKeyLength() / 8);
            dkEncr.prepare(saaj.getSOAPPart());
            Element encrDKTokenElem = null;
            encrDKTokenElem = dkEncr.getdktElement();
            addDerivedKeyElement(encrDKTokenElem);
            Element refList = dkEncr.encryptForExternalRef(null, encrParts);
            if (atEnd) {
                this.insertBeforeBottomUp(refList);
            } else {
                this.addDerivedKeyElement(refList);                        
            }
            return dkEncr;
        } catch (Exception e) {
            policyNotAsserted(recToken, e);
        }
        return null;
    }
    
    private WSSecBase doEncryption(AbstractTokenWrapper recToken,
                                   SecurityToken encrTok,
                                   boolean attached,
                                   List<WSEncryptionPart> encrParts,
                                   boolean atEnd) {
        //Do encryption
        if (recToken != null && recToken.getToken() != null && encrParts.size() > 0) {
            AbstractToken encrToken = recToken.getToken();
            policyAsserted(recToken);
            policyAsserted(encrToken);
            AlgorithmSuite algorithmSuite = sbinding.getAlgorithmSuite();
            if (encrToken.getDerivedKeys() == DerivedKeys.RequireDerivedKeys) {
                return doEncryptionDerived(recToken, encrTok, encrToken,
                                           attached, encrParts, atEnd);
            } else {
                try {
                    WSSecEncrypt encr = new WSSecEncrypt(wssConfig);
                    String encrTokId = encrTok.getId();
                    if (attached) {
                        encrTokId = encrTok.getWsuId();
                        if (encrTokId == null 
                            && (encrToken instanceof SecureConversationToken
                                || encrToken instanceof SecurityContextToken)) {
                            encr.setEncKeyIdDirectId(true);
                            encrTokId = encrTok.getId();
                        } else if (encrTokId == null) {
                            encrTokId = encrTok.getId();
                        }
                        if (encrTokId.startsWith("#")) {
                            encrTokId = encrTokId.substring(1);
                        }
                    } else {
                        encr.setEncKeyIdDirectId(true);
                    }
                    if (encrTok.getTokenType() != null) {
                        encr.setCustomReferenceValue(encrTok.getTokenType());
                    }
                    encr.setEncKeyId(encrTokId);
                    encr.setEphemeralKey(encrTok.getSecret());
                    Crypto crypto = getEncryptionCrypto(recToken);
                    if (crypto != null) {
                        this.message.getExchange().put(SecurityConstants.ENCRYPT_CRYPTO, crypto);
                        setEncryptionUser(encr, recToken, false, crypto);
                    }
                    
                    encr.setDocument(saaj.getSOAPPart());
                    encr.setEncryptSymmKey(false);
                    encr.setSymmetricEncAlgorithm(algorithmSuite.getAlgorithmSuiteType().getEncryption());
                    
                    if (encrToken instanceof IssuedToken || encrToken instanceof SpnegoContextToken) {
                        //Setting the AttachedReference or the UnattachedReference according to the flag
                        Element ref;
                        if (attached) {
                            ref = encrTok.getAttachedReference();
                        } else {
                            ref = encrTok.getUnattachedReference();
                        }

                        String tokenType = encrTok.getTokenType();
                        if (ref != null) {
                            SecurityTokenReference secRef = 
                                new SecurityTokenReference(cloneElement(ref), new BSPEnforcer());
                            encr.setSecurityTokenReference(secRef);
                        } else if (WSConstants.WSS_SAML_TOKEN_TYPE.equals(tokenType)
                            || WSConstants.SAML_NS.equals(tokenType)) {
                            encr.setCustomReferenceValue(WSConstants.WSS_SAML_KI_VALUE_TYPE);
                            encr.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                        } else if (WSConstants.WSS_SAML2_TOKEN_TYPE.equals(tokenType)
                            || WSConstants.SAML2_NS.equals(tokenType)) {
                            encr.setCustomReferenceValue(WSConstants.WSS_SAML2_KI_VALUE_TYPE);
                            encr.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                        } else {
                            encr.setCustomReferenceValue(tokenType);
                            encr.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                        }
                    } else if (encrToken instanceof UsernameToken) {
                        encr.setCustomReferenceValue(WSConstants.WSS_USERNAME_TOKEN_VALUE_TYPE);
                    } else if (!isRequestor()) {
                        if (encrTok.getSHA1() != null) {
                            encr.setCustomReferenceValue(encrTok.getSHA1());
                            encr.setKeyIdentifierType(WSConstants.ENCRYPTED_KEY_SHA1_IDENTIFIER);
                        } else {
                            encr.setKeyIdentifierType(WSConstants.EMBED_SECURITY_TOKEN_REF);
                        }
                    }

                    encr.prepare(saaj.getSOAPPart(), crypto);
                   
                    if (encr.getBSTTokenId() != null) {
                        encr.prependBSTElementToHeader(secHeader);
                    }
                   
                   
                    Element refList = encr.encryptForRef(null, encrParts);
                    if (atEnd) {
                        this.insertBeforeBottomUp(refList);
                    } else {
                        this.addDerivedKeyElement(refList);                        
                    }
                    return encr;
                } catch (WSSecurityException e) {
                    policyNotAsserted(recToken, e);
                }    
            }
        }
        return null;
    }    
    
    private byte[] doSignatureDK(List<WSEncryptionPart> sigs,
                               AbstractTokenWrapper policyAbstractTokenWrapper, 
                               AbstractToken policyToken, 
                               SecurityToken tok,
                               boolean included) throws WSSecurityException {
        Document doc = saaj.getSOAPPart();
        WSSecDKSign dkSign = new WSSecDKSign(wssConfig);
        if (policyAbstractTokenWrapper.getToken().getVersion() == SPConstants.SPVersion.SP12) {
            dkSign.setWscVersion(ConversationConstants.VERSION_05_12);
        }
        
        //Check for whether the token is attached in the message or not
        boolean attached = false;
        if (includeToken(policyToken.getIncludeTokenType())) {
            attached = true;
        }
        
        // Setting the AttachedReference or the UnattachedReference according to the flag
        Element ref;
        if (attached) {
            ref = tok.getAttachedReference();
        } else {
            ref = tok.getUnattachedReference();
        }
        
        if (ref != null) {
            dkSign.setExternalKey(tok.getSecret(), cloneElement(ref));
        } else if (!isRequestor() && policyToken.getDerivedKeys() 
            == DerivedKeys.RequireDerivedKeys && tok.getSHA1() != null) {            
            // If the Encrypted key used to create the derived key is not
            // attached use key identifier as defined in WSS1.1 section
            // 7.7 Encrypted Key reference
            SecurityTokenReference tokenRef = new SecurityTokenReference(doc);
            if (tok.getSHA1() != null) {
                tokenRef.setKeyIdentifierEncKeySHA1(tok.getSHA1());
                String tokenType = tok.getTokenType();
                if (tokenType == null) {
                    tokenType = WSConstants.WSS_ENC_KEY_VALUE_TYPE;
                }
                tokenRef.addTokenType(tokenType);
            }
            dkSign.setExternalKey(tok.getSecret(), tokenRef.getElement());
        } else {
            if ((!attached && !isRequestor()) || policyToken instanceof SecureConversationToken 
                || policyToken instanceof SecurityContextToken) {
                dkSign.setTokenIdDirectId(true);
            }
            dkSign.setExternalKey(tok.getSecret(), tok.getId());
        }

        //Set the algo info
        dkSign.setSignatureAlgorithm(sbinding.getAlgorithmSuite().getSymmetricSignature());
        AlgorithmSuiteType algType = sbinding.getAlgorithmSuite().getAlgorithmSuiteType();
        dkSign.setDerivedKeyLength(algType.getSignatureDerivedKeyLength() / 8);
        if (tok.getSHA1() != null) {
            //Set the value type of the reference
            String tokenType = tok.getTokenType();
            if (tokenType == null) {
                tokenType = WSConstants.WSS_ENC_KEY_VALUE_TYPE;
            }
            dkSign.setCustomValueType(tokenType);
        } else {
            String tokenType = tok.getTokenType();
            if (WSConstants.WSS_SAML_TOKEN_TYPE.equals(tokenType)
                || WSConstants.SAML_NS.equals(tokenType)) {
                dkSign.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                dkSign.setCustomValueType(WSConstants.WSS_SAML_KI_VALUE_TYPE);
            } else if (WSConstants.WSS_SAML2_TOKEN_TYPE.equals(tokenType)
                || WSConstants.SAML2_NS.equals(tokenType)) {
                dkSign.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                dkSign.setCustomValueType(WSConstants.WSS_SAML2_KI_VALUE_TYPE);
            } else if (policyToken instanceof UsernameToken) {
                dkSign.setCustomValueType(WSConstants.WSS_USERNAME_TOKEN_VALUE_TYPE);
            } else {
                dkSign.setCustomValueType(tokenType);
            }
        }
        
        try {
            dkSign.prepare(doc, secHeader);
        } catch (ConversationException e) {
            throw new WSSecurityException(WSSecurityException.ErrorCode.FAILURE, e);
        }
        
        if (sbinding.isProtectTokens()) {
            String sigTokId = tok.getId();
            if (included) {
                sigTokId = tok.getWsuId();
                if (sigTokId == null) {
                    sigTokId = tok.getId();
                }
                if (sigTokId.startsWith("#")) {
                    sigTokId = sigTokId.substring(1);
                }
            }
            sigs.add(new WSEncryptionPart(sigTokId));
        }
        
        dkSign.setParts(sigs);
        List<Reference> referenceList = dkSign.addReferencesToSign(sigs, secHeader);
        
        //Add elements to header
        Element el = dkSign.getdktElement();
        addDerivedKeyElement(el);
        
        //Do signature
        if (bottomUpElement == null) {
            dkSign.computeSignature(referenceList, false, null);
        } else {
            dkSign.computeSignature(referenceList, true, bottomUpElement);
        }
        bottomUpElement = dkSign.getSignatureElement();
        
        this.mainSigId = dkSign.getSignatureId();

        return dkSign.getSignatureValue();        
    }
    
    private byte[] doSignature(List<WSEncryptionPart> sigs,
                             AbstractTokenWrapper policyAbstractTokenWrapper, 
                             AbstractToken policyToken, 
                             SecurityToken tok,
                             boolean included) throws WSSecurityException {
        if (policyToken.getDerivedKeys() == DerivedKeys.RequireDerivedKeys) {
            return doSignatureDK(sigs, policyAbstractTokenWrapper, policyToken, tok, included);
        } else {
            WSSecSignature sig = new WSSecSignature(wssConfig);
            // If a EncryptedKeyToken is used, set the correct value type to
            // be used in the wsse:Reference in ds:KeyInfo
            int type = included ? WSConstants.CUSTOM_SYMM_SIGNING 
                : WSConstants.CUSTOM_SYMM_SIGNING_DIRECT;
            if (policyToken instanceof X509Token) {
                if (isRequestor()) {
                    sig.setCustomTokenValueType(
                        WSConstants.SOAPMESSAGE_NS11 + "#" + WSConstants.ENC_KEY_VALUE_TYPE
                    );
                    sig.setKeyIdentifierType(type);
                } else {
                    //the tok has to be an EncryptedKey token
                    sig.setEncrKeySha1value(tok.getSHA1());
                    sig.setKeyIdentifierType(WSConstants.ENCRYPTED_KEY_SHA1_IDENTIFIER);
                }
            } else if (policyToken instanceof UsernameToken) {
                sig.setCustomTokenValueType(WSConstants.WSS_USERNAME_TOKEN_VALUE_TYPE);
                sig.setKeyIdentifierType(type);
            } else {
                //Setting the AttachedReference or the UnattachedReference according to the flag
                Element ref;
                if (included) {
                    ref = tok.getAttachedReference();
                } else {
                    ref = tok.getUnattachedReference();
                }
                
                if (ref != null) {
                    SecurityTokenReference secRef = 
                        new SecurityTokenReference(cloneElement(ref), new BSPEnforcer());
                    sig.setSecurityTokenReference(secRef);
                    sig.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                } else {
                    String tokenType = tok.getTokenType();
                    if (WSConstants.WSS_SAML_TOKEN_TYPE.equals(tokenType)
                        || WSConstants.SAML_NS.equals(tokenType)) {
                        sig.setCustomTokenValueType(WSConstants.WSS_SAML_KI_VALUE_TYPE);
                        sig.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                    } else if (WSConstants.WSS_SAML2_TOKEN_TYPE.equals(tokenType)
                        || WSConstants.SAML2_NS.equals(tokenType)) {
                        sig.setCustomTokenValueType(WSConstants.WSS_SAML2_KI_VALUE_TYPE);
                        sig.setKeyIdentifierType(WSConstants.CUSTOM_KEY_IDENTIFIER);
                    } else {
                        sig.setCustomTokenValueType(tokenType);
                        sig.setKeyIdentifierType(type);
                    }
                }
            }
            
            String sigTokId;
            if (included) {
                sigTokId = tok.getWsuId();
                if (sigTokId == null) {
                    if (policyToken instanceof SecureConversationToken
                        || policyToken instanceof SecurityContextToken) {
                        sig.setKeyIdentifierType(WSConstants.CUSTOM_SYMM_SIGNING_DIRECT);
                    }
                    sigTokId = tok.getId();                    
                }
                if (sigTokId.startsWith("#")) {
                    sigTokId = sigTokId.substring(1);
                }
            } else {
                sigTokId = tok.getId();
            }
                      
            if (included && sbinding.isProtectTokens()) {
                sigs.add(new WSEncryptionPart(sigTokId));
            }
            
            sig.setCustomTokenId(sigTokId);
            sig.setSecretKey(tok.getSecret());
            sig.setSignatureAlgorithm(sbinding.getAlgorithmSuite().getSymmetricSignature());
            Crypto crypto = null;
            if (sbinding.getProtectionToken() != null) {
                crypto = getEncryptionCrypto(sbinding.getProtectionToken());
            } else {
                crypto = getSignatureCrypto(policyAbstractTokenWrapper);
            }
            this.message.getExchange().put(SecurityConstants.SIGNATURE_CRYPTO, crypto);
            sig.prepare(saaj.getSOAPPart(), crypto, secHeader);
            sig.setParts(sigs);
            List<Reference> referenceList = sig.addReferencesToSign(sigs, secHeader);

            //Do signature
            if (bottomUpElement == null) {
                sig.computeSignature(referenceList, false, null);
            } else {
                sig.computeSignature(referenceList, true, bottomUpElement);
            }
            bottomUpElement = sig.getSignatureElement();

            this.mainSigId = sig.getId();
            return sig.getSignatureValue();
        }
    }

    private String setupEncryptedKey(AbstractTokenWrapper wrapper, AbstractToken sigToken) throws WSSecurityException {
        WSSecEncryptedKey encrKey = this.getEncryptedKeyBuilder(wrapper, sigToken);
        String id = encrKey.getId();
        byte[] secret = encrKey.getEphemeralKey();

        Date created = new Date();
        Date expires = new Date();
        expires.setTime(created.getTime() + 300000);
        SecurityToken tempTok = new SecurityToken(
                        id, 
                        encrKey.getEncryptedKeyElement(),
                        created, 
                        expires);
        
        
        tempTok.setSecret(secret);
        
        // Set the SHA1 value of the encrypted key, this is used when the encrypted
        // key is referenced via a key identifier of type EncryptedKeySHA1
        tempTok.setSHA1(getSHA1(encrKey.getEncryptedEphemeralKey()));
        
        tokenStore.add(tempTok);
        
        String bstTokenId = encrKey.getBSTTokenId();
        //If direct ref is used to refer to the cert
        //then add the cert to the sec header now
        if (bstTokenId != null && bstTokenId.length() > 0) {
            encrKey.prependBSTElementToHeader(secHeader);
        }
        return id;
    }
    
    private String setupUTDerivedKey(UsernameToken sigToken) throws WSSecurityException {
        boolean useMac = hasSignedPartsOrElements();
        WSSecUsernameToken usernameToken = addDKUsernameToken(sigToken, useMac);
        String id = usernameToken.getId();
        byte[] secret = usernameToken.getDerivedKey();

        Date created = new Date();
        Date expires = new Date();
        expires.setTime(created.getTime() + 300000);
        SecurityToken tempTok = 
            new SecurityToken(id, usernameToken.getUsernameTokenElement(), created, expires);
        tempTok.setSecret(secret);
        
        tokenStore.add(tempTok);
        
        return id;
    }
    
    private String getEncryptedKey() {
        
        List<WSHandlerResult> results = CastUtils.cast((List<?>)message.getExchange().getInMessage()
            .get(WSHandlerConstants.RECV_RESULTS));
        
        for (WSHandlerResult rResult : results) {
            List<WSSecurityEngineResult> wsSecEngineResults = rResult.getResults();
            
            for (WSSecurityEngineResult wser : wsSecEngineResults) {
                Integer actInt = (Integer)wser.get(WSSecurityEngineResult.TAG_ACTION);
                String encryptedKeyID = (String)wser.get(WSSecurityEngineResult.TAG_ID);
                if (actInt.intValue() == WSConstants.ENCR
                    && encryptedKeyID != null
                    && encryptedKeyID.length() != 0) {
                    Date created = new Date();
                    Date expires = new Date();
                    expires.setTime(created.getTime() + 300000);
                    SecurityToken tempTok = new SecurityToken(encryptedKeyID, created, expires);
                    tempTok.setSecret((byte[])wser.get(WSSecurityEngineResult.TAG_SECRET));
                    tempTok.setSHA1(getSHA1((byte[])wser
                                            .get(WSSecurityEngineResult.TAG_ENCRYPTED_EPHEMERAL_KEY)));
                    tokenStore.add(tempTok);
                    
                    return encryptedKeyID;
                }
            }
        }
        return null;
    }
    
    private String getUTDerivedKey() throws WSSecurityException {
        
        List<WSHandlerResult> results = CastUtils.cast((List<?>)message.getExchange().getInMessage()
            .get(WSHandlerConstants.RECV_RESULTS));
        
        for (WSHandlerResult rResult : results) {
            List<WSSecurityEngineResult> wsSecEngineResults = rResult.getResults();
            
            for (WSSecurityEngineResult wser : wsSecEngineResults) {
                Integer actInt = (Integer)wser.get(WSSecurityEngineResult.TAG_ACTION);
                String utID = (String)wser.get(WSSecurityEngineResult.TAG_ID);
                if (actInt.intValue() == WSConstants.UT_NOPASSWORD) {
                    if (utID == null || utID.length() == 0) {
                        utID = wssConfig.getIdAllocator().createId("UsernameToken-", null);
                    }
                    Date created = new Date();
                    Date expires = new Date();
                    expires.setTime(created.getTime() + 300000);
                    SecurityToken tempTok = new SecurityToken(utID, created, expires);
                    
                    byte[] secret = (byte[])wser.get(WSSecurityEngineResult.TAG_SECRET);
                    tempTok.setSecret(secret);
                    tokenStore.add(tempTok);

                    return utID;
                }
            }
        }
        return null;
    }
    
    private String getSHA1(byte[] input) {
        try {
            byte[] digestBytes = WSSecurityUtil.generateDigest(input);
            return Base64.encode(digestBytes);
        } catch (WSSecurityException e) {
            //REVISIT
        }
        return null;
    }
    
    private boolean hasSignedPartsOrElements() {
        Collection<AssertionInfo> ais = aim.getAssertionInfo(SP12Constants.SIGNED_PARTS);
        if (ais != null && ais.size() > 0) {
            return true;
        }
        ais = aim.getAssertionInfo(SP12Constants.SIGNED_ELEMENTS);
        if (ais != null && ais.size() > 0) {
            return true;
        }
        return false;
    }

}
